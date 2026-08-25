# Sprint 2 板块 B：广告投放数据模块验收记录

> 验收日期：2026-08-26
>
> 验收结论：B1～B5 已完成，可以进入 PR Review。

## 1. 已实现接口

| 方法 | 路径 | 能力 | 权限 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/advertising-types` | 查询广告类型字典 | `ADMIN`、`OPERATOR` |
| `POST` | `/api/v1/delivery-records` | 录入一条投放数据 | `ADMIN`、`OPERATOR` |
| `GET` | `/api/v1/delivery-records` | 组合筛选并分页查询 | `ADMIN`、`OPERATOR` |
| `GET` | `/api/v1/delivery-records/{id}` | 查询投放记录详情 | `ADMIN`、`OPERATOR` |
| `PATCH` | `/api/v1/delivery-records/{id}` | 局部修正投放记录 | `ADMIN` |
| `DELETE` | `/api/v1/delivery-records/{id}` | 删除未关联资金流水的误录数据 | `ADMIN` |

列表接口支持 `startDate`、`endDate`、`advertiserId`、`advertisingTypeCode`、`page`、`size`。结果按 `record_date DESC, id DESC` 稳定排序，使用数据库物理分页，并通过一次关联查询返回广告主和广告类型名称，未引入 N+1 查询。

## 2. 核心业务规则

- 广告主和广告类型必须存在且处于启用状态。
- `externalRecordNo` 全局唯一；录入使用原子条件插入，并发重复请求也只会成功一次。
- 展示、点击、转化和花费不能为负，且满足 `conversions <= clicks <= impressions`。
- 查询开始日期不能晚于结束日期；同时提供起止日期时，包含首尾日期且跨度不能超过 366 天。
- 每页最多返回 100 条记录；不存在的广告类型筛选直接返回空页。
- 局部修正只更新请求中提供的字段，`externalRecordNo` 不允许修改；修改关联对象时重新校验其启用状态。
- 删除使用带 `NOT EXISTS` 的单条条件 SQL。未被资金流水引用的记录可物理删除；已被引用时返回 `409 DELIVERY_RECORD_IN_USE` 并保留业务历史。
- 数据库外键继续作为并发删除/关联竞态的最终保护；约束冲突会转换为统一的 409 业务错误。

## 3. 主要错误响应

| HTTP 状态 | 错误码 | 场景 |
| --- | --- | --- |
| `400` | `DELIVERY_INVALID_METRICS` | 指标或花费不符合约束 |
| `400` | `DELIVERY_INVALID_DATE_RANGE` | 开始日期晚于结束日期 |
| `400` | `DELIVERY_DATE_RANGE_TOO_LARGE` | 查询日期跨度超过 366 天 |
| `400` | `DELIVERY_NO_FIELDS_TO_UPDATE` | PATCH 未提供任何可修改字段 |
| `404` | `DELIVERY_ADVERTISER_NOT_FOUND` | 广告主不存在 |
| `404` | `DELIVERY_ADVERTISING_TYPE_NOT_FOUND` | 广告类型不存在 |
| `404` | `DELIVERY_RECORD_NOT_FOUND` | 投放记录不存在 |
| `409` | `DELIVERY_EXTERNAL_RECORD_NO_ALREADY_EXISTS` | 外部记录号重复 |
| `409` | `DELIVERY_RECORD_IN_USE` | 投放记录已关联资金流水，禁止删除 |

认证失败、权限不足和参数校验错误继续复用项目统一响应结构。

## 4. 自动化验收结果

执行命令：

```powershell
.\scripts\test.cmd
```

结果：项目全量测试 `215/215` 通过。

B5 删除能力还单独执行了 Service、MockMvc 和 PostgreSQL 持久化专项测试，共 `68/68` 通过。覆盖内容包括：

- ADMIN/OPERATOR 删除权限边界。
- 删除成功、记录不存在 404、存在资金流水 409。
- 条件删除与数据库外键冲突兜底。
- 未引用记录确实被物理删除；被引用记录及对应资金流水均被保留。
- B1～B4 的录入、查询、分页和修正能力回归。

## 5. B1～B5 提交记录

| 模块 | Commit | 内容 |
| --- | --- | --- |
| B1 | `9c219c8` | 广告类型查询接口 |
| B2 | `9c660a8` | 投放记录原子录入 |
| B3 | `8dffe97` | 组合筛选、详情和物理分页 |
| B4 | `79bb83b` | ADMIN 局部修正 |
| B5 | `80b37d1` | 受保护删除及完整测试 |

## 6. 模块边界

板块 B 只负责维护可信的投放事实数据，以下能力不在本板块内：

- 时间、广告主和广告类型维度的聚合统计及报表接口：板块 C。
- 根据投放记录扣减余额并生成消费流水：板块 D。
- 30 天多广告主、多广告类型的完整演示数据和报表展示：在板块 C 及 Sprint 2 Demo 阶段统一补充。

因此，修正投放记录不会隐式调整历史资金流水；已关联流水的记录也不能删除。
