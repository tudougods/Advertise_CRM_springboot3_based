# Sprint 2 板块 C：统计与报表模块验收记录

## 1. 验收范围

验收日期：2026-08-26。

板块 C 基于 `advertising_delivery_records` 提供简化 BI 能力，不在 Java 内存中加载明细后聚合。实际聚合 SQL 位于 `src/main/resources/mapper/report/DeliveryReportMapper.xml`。

| 接口 | 能力 | 权限 |
| --- | --- | --- |
| `GET /api/v1/reports/delivery/overview` | 指标总览 | `ADMIN`、`OPERATOR` |
| `GET /api/v1/reports/delivery/trend` | `DAY/WEEK/MONTH` 时间趋势 | `ADMIN`、`OPERATOR` |
| `GET /api/v1/reports/delivery/by-advertiser` | 广告主维度、白名单排序和分页 | `ADMIN`、`OPERATOR` |
| `GET /api/v1/reports/delivery/by-ad-type` | 广告类型维度汇总 | `ADMIN`、`OPERATOR` |

所有接口支持 `startDate`、`endDate`、`advertiserId` 和 `advertisingTypeCode`。日期均不提供时默认查询包含当天的最近 30 天；起止日期必须成对提供，最多允许 366 个自然日。

## 2. 指标口径

所有比率基于分组后的汇总值重新计算，不平均明细行比率。

| 指标 | SQL 口径 | 返回精度 |
| --- | --- | --- |
| 展示量 | `SUM(impressions)` | 整数 |
| 点击量 | `SUM(clicks)` | 整数 |
| 转化量 | `SUM(conversions)` | 整数 |
| 花费 | `SUM(spend)` | 2 位小数 |
| CTR | `SUM(clicks) / NULLIF(SUM(impressions), 0)` | 4 位小数 |
| CVR | `SUM(conversions) / NULLIF(SUM(clicks), 0)` | 4 位小数 |
| CPC | `SUM(spend) / NULLIF(SUM(clicks), 0)` | 2 位小数 |

分母为零或没有匹配数据时使用 `COALESCE` 返回稳定的零值，不返回 `null`、`NaN` 或数据库异常。

## 3. 固定数据集验收

PostgreSQL 集成测试使用明确的投放记录逐项核对结果，不只断言 HTTP 200。

### 3.1 指标总览

固定数据集在目标日期范围内包含三条记录，人工汇总结果如下：

| 展示量 | 点击量 | 转化量 | 花费 | CTR | CVR | CPC |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 3500 | 300 | 30 | 650.00 | 0.0857 | 0.1000 | 2.17 |

测试同时覆盖广告主筛选、广告类型筛选、空数据，以及存在花费但点击量为零的除零场景。

### 3.2 时间趋势

趋势数据跨越 `2026-01-31`、`2026-02-01`、`2026-02-02`、`2026-02-28` 和 `2026-03-01`：

- `DAY` 保留原始 `LocalDate`，按日期升序返回。
- `WEEK` 使用 ISO 周，周一为时间桶开始日期；`2026-01-31` 与 `2026-02-01` 正确合并到 `2026-01-26`。
- `MONTH` 使用自然月；2026 年 2 月重新汇总得到展示量 3500、点击量 150、CTR 0.0429。
- 测试加入其他广告主和其他广告类型的干扰记录，确认两个筛选条件都生效。

### 3.3 广告主和广告类型维度

- 广告主维度验证总数为 3，第一页和第二页无重复、无遗漏。
- `IMPRESSIONS`、`CLICKS`、`CONVERSIONS`、`SPEND`、`CTR`、`CVR`、`CPC` 七个排序字段均在 PostgreSQL 上执行。
- 排序字段和方向由枚举进入 MyBatis `<choose>`，没有 `${...}` SQL 字符串插值。
- 数据查询和 `COUNT(DISTINCT advertiser_id)` 共用同一个过滤 SQL 片段。
- 广告类型维度验证 `SEARCH` 汇总为展示量 3000、点击量 200、转化量 30、花费 700.00、CTR 0.0667、CVR 0.1500、CPC 3.50。

## 4. 接口与安全验收

MockMvc 和 OpenAPI 自动化测试确认：

- `ADMIN`、`OPERATOR` 可以查询四类报表。
- 缺少或无效 JWT 返回统一 401。
- 空白广告类型、非法时间粒度、非法排序字段和超大分页参数返回统一 400。
- 四个路径均出现在 `/v3/api-docs`，可通过 Swagger UI 直接演示。
- 报表响应统一使用 `ApiResponse<T>`；广告主分页使用 `PageResponse<T>`。

## 5. `EXPLAIN ANALYZE` 验收

验证脚本：`scripts/sprint2-report-explain.sql`。

脚本在事务内创建 20 个临时广告主和 60000 条投放记录，刷新统计信息后执行真实查询计划，最后 `ROLLBACK` 并重新 `ANALYZE`。演示数据不会保留在数据库中。

本机 PostgreSQL 16 验证摘要：

| 查询场景 | 主要执行节点 | 命中索引 | 实际执行时间 |
| --- | --- | --- | ---: |
| 广告主 + 日期 + 广告类型总览 | `Bitmap Heap Scan` | `idx_advertising_delivery_advertiser_date` | 约 0.48 ms |
| 31 天日趋势 | `Bitmap Heap Scan` + `HashAggregate` | `idx_advertising_delivery_record_date` | 约 1.58 ms |
| 广告类型 + 日期的广告主汇总 | `Bitmap Heap Scan` + `GroupAggregate` | `idx_advertising_delivery_type_date` | 约 0.63 ms |

执行时间仅表示本机固定数据规模下的结果，不作为生产 SLA。三个主要访问模式都使用了与过滤前缀匹配的既有索引，因此板块 C 不新增 `V8` 索引迁移。小数据量下 PostgreSQL 选择顺序扫描仍属正常行为。

手动复现命令：

```powershell
psql -h localhost -p 15432 -U crm_user -d advertiser_crm -f scripts/sprint2-report-explain.sql
```

数据库密码通过本机环境变量或交互提示提供，不写入脚本和仓库。

## 6. 自动化结果与结论

最终执行：

```powershell
.\mvnw.cmd test
```

验收结果：273 项测试通过，0 失败，0 错误，0 跳过。其中报表测试覆盖查询规范化、统一指标、四类接口、RBAC、OpenAPI、固定数据集 SQL、日期边界、除零、筛选、排序和分页。

板块 C 已达到完成标准：四类报表可通过 Swagger 演示，固定数据集结果与人工计算一致，日期使用 `DATE/LocalDate`，并留下可重复执行的 SQL 性能证据。
