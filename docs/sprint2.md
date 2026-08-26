# Advertiser CRM Sprint 2 开发计划

> Sprint 主题：数据与交易模块开发（核心业务能力）
>
> 计划周期：第 3 周，建议 5 个工作日
>
> 计划依据：当前 `main` 分支代码、Sprint 1 文档和导师给出的第三周产出要求

## 1. 当前项目 Review

### 1.1 已具备的能力

当前项目是 Java 21、Spring Boot 3.5、MyBatis-Plus、PostgreSQL、Flyway 组成的模块化单体后端，已经完成 Sprint 2 可以直接复用的基础能力：

- `auth`：注册、登录、JWT 鉴权和认证接口限流。
- `user`：用户、角色、状态管理，以及“至少保留一个启用管理员”的事务保护。
- `advertiser`：广告主和广告主分类 CRUD、分页、状态及关联规则。
- `common`：统一响应、统一异常、请求参数校验、Request ID 和请求日志。
- `config`：Spring Security、MyBatis-Plus 分页、Swagger/OpenAPI。
- 数据层：Flyway `V1`、`V2` 迁移，当前核心表为 `users`、`advertiser_categories`、`advertisers`。
- 测试层：Service 单元测试、MockMvc 权限测试和 PostgreSQL 持久化测试均已有范例。

当前代码采用“按业务模块分包，模块内部按 `controller/service/mapper/entity/dto/exception` 分层”的约定。Sprint 2 应继续增加独立的 `delivery`、`report`、`account`、`payment` 模块，不把新业务堆入已有 `advertiser` 模块。

### 1.2 当前缺口与约束

- 尚无广告投放数据、广告类型、账户、流水和支付订单相关表及代码。
- 尚无复杂聚合 SQL、报表接口或统计展示页面。
- 当前仓库是纯后端项目，没有前端工程；本 Sprint 的统计 Demo 默认使用“模拟数据 + Swagger 报表接口 + pgAdmin SQL 验证”，简单图表页面只作为扩展项。
- 列表接口已经统一使用数据库物理分页，Sprint 2 明细查询必须延续 `PageResponse` 和 `size <= 100` 约束。
- 已执行的 `V1`、`V2` 不得修改；新表、约束和索引通过后续 Flyway 版本添加。
- 金额统一使用 `BigDecimal` 和 PostgreSQL `NUMERIC(19,2)`，不得使用 `double`/`float`。
- 当前测试依赖本机 PostgreSQL。Review 时执行 107 项测试，其中 98 项通过，9 项数据库集成测试因 `localhost:15432` 未启动而报连接拒绝；开始 Sprint 2 前应启动数据库并恢复全量测试通过。

## 2. Sprint 目标

本迭代完成一条可以演示和验证的业务闭环：

```text
广告主 -> 录入投放数据 -> 多维统计报表
   |
   +-> 创建账户 -> 创建充值订单 -> 模拟支付/回调 -> 余额增加
   |
   +-> 记录广告消费 -> 原子扣减余额 -> 生成资金流水
```

Sprint 完成时应满足：

1. 能按时间、广告主和广告类型录入、分页查询投放数据。
2. 能按日/周/月、广告主、广告类型返回汇总报表。
3. 能查询广告主账户余额和充值/消费流水。
4. 能演示支付订单从 `PENDING` 到 `SUCCESS`，并通过幂等回调只入账一次。
5. 并发消费不会产生负余额，重复业务请求不会重复扣款或充值。
6. 核心查询有与访问模式匹配的索引，并用 `EXPLAIN ANALYZE` 留下优化前后或命中索引的证据。

## 3. 范围与优先级

### 3.1 本 Sprint 必做（MVP）

- 投放数据单条录入、分页查询和组合筛选。
- 时间、广告主、广告类型三个维度的统计接口。
- 概览、趋势和分组报表接口。
- 广告主账户、余额查询、充值和消费流水。
- 模拟支付订单、模拟支付入口、带签名的回调处理。
- 回调幂等、消费幂等、余额不足和并发扣款保护。
- Flyway 迁移、核心 SQL 说明、索引和自动化测试。
- 可重复执行的模拟数据及 Swagger Demo 流程。

### 3.2 可选扩展

- CSV 批量导入。
- CSV/Excel 报表导出。
- 简单静态图表页。
- Caffeine 短时缓存。
- 退款、人工调账和真实第三方支付 SDK。

扩展项不得挤占 MVP 的事务一致性、幂等性和测试时间。

## 4. 模块划分与开发任务

## 4.1 板块 A：数据库与公共设计

> 当前状态：已完成。`V3`～`V5`、Java 持久层、核心 SQL 说明和空库验收均已落地，详见 `docs/sprint2-database-design.md`。

### 目标

先固定数据模型、状态枚举、金额精度和模块边界，避免数据模块和账户模块并行开发时反复改表。

### 计划表结构

| 表 | 用途 | 关键规则 |
| --- | --- | --- |
| `advertising_types` | 广告类型字典 | `code` 唯一，包含名称和启用状态；预置 `SEARCH`、`DISPLAY`、`VIDEO`、`SOCIAL` |
| `advertising_delivery_records` | 每日投放数据事实表 | 广告主、日期、广告类型和外部记录号；指标非负 |
| `advertiser_accounts` | 广告主资金账户 | 一个广告主一个账户；余额不得小于 0 |
| `advertiser_account_transactions` | 不可变资金流水 | 业务流水号唯一；金额为正；保存变更后余额 |
| `recharge_orders` | 模拟充值订单 | 订单号唯一；维护支付状态和支付时间 |
| `recharge_payment_callbacks` | 支付回调审计与幂等 | 支付方事件号唯一；记录处理结果，不记录敏感密钥 |

已实现迁移：

- `V3__create_advertising_tables.sql`
- `V4__create_advertiser_account_tables.sql`
- `V5__create_recharge_payment_tables.sql`
- `V6__protect_delivery_account_consistency.sql`（板块 B review 后补充账户流水与投放记录的广告主一致性保护）
- `V7__serialize_delivery_account_consistency.sql`（为流水关联与投放修正增加统一行锁顺序）
- `V8__protect_recharge_account_consistency.sql`（整体 review 后补充充值订单与资金流水的账户一致性保护）
- 后续如需补充约束或索引，继续创建新的迁移，不回改已执行迁移。

### 核心字段约定

`advertising_delivery_records` 实际包含：

- `id`
- `external_record_no`：外部/导入记录号，用于防止重复入库
- `advertiser_id`
- `advertising_type_id`
- `record_date`
- `impressions`
- `clicks`
- `conversions`
- `spend`
- `created_at`、`updated_at`

数据库约束：指标和花费不得为负，`clicks <= impressions`，`conversions <= clicks`，`external_record_no` 唯一。

账户通过外键关联 `advertisers`，充值订单和回调再依次关联账户与订单。存在资金、投放或充值历史的广告主不能物理删除；当前实现会返回 `409 ADVERTISER_HAS_BUSINESS_DATA`，避免级联删除审计数据。

### 任务

- ✅ 绘制 Sprint 2 ER 关系并确认删除策略。
- ✅ 编写 `V3`～`V5` Flyway 迁移。
- ✅ 为状态、金额、唯一性和外键添加数据库约束。
- ✅ 创建实体、枚举和 Mapper 基础结构。
- ✅ 在 `docs/sprint2-database-design.md` 记录表设计、核心 SQL 和索引理由。
- ✅ 验证空库迁移和从现有 `V2` 升级两种路径。

### 完成标准

- 全新数据库可以一次迁移到最新版本。
- 已有 Sprint 1 数据库可以无损升级。
- 直接执行非法 SQL 时，负金额、重复业务号和非法状态会被数据库拒绝。

## 4.2 板块 B：广告投放数据模块 `delivery`

> 当前状态：已完成。广告类型查询、投放数据录入、组合筛选分页、详情、修正、受保护删除、接口权限和三层测试均已落地，详见 `docs/sprint2-delivery-acceptance.md`。

### 目标

完成投放数据的入库、详情和可筛选分页查询，为报表模块提供可信数据源。

### 建议接口

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/delivery-records` | 录入一条投放数据 | `ADMIN`、`OPERATOR` |
| `GET` | `/api/v1/delivery-records` | 日期、广告主、类型组合筛选并分页 | `ADMIN`、`OPERATOR` |
| `GET` | `/api/v1/delivery-records/{id}` | 查询投放明细 | `ADMIN`、`OPERATOR` |
| `PATCH` | `/api/v1/delivery-records/{id}` | 修正投放数据 | `ADMIN` |
| `DELETE` | `/api/v1/delivery-records/{id}` | 删除误录数据 | `ADMIN` |

列表参数建议为 `startDate`、`endDate`、`advertiserId`、`advertisingTypeCode`、`page`、`size`。默认限制查询日期范围，日期跨度上限建议为 366 天，防止误查全表。

### 业务规则

- 广告主和广告类型必须存在且处于可用状态。
- 同一 `externalRecordNo` 重复提交返回 409，不能重复入库。
- `startDate` 不得晚于 `endDate`。
- `startDate`、`endDate` 必须成对提供；均不提供时默认查询最近 30 天，最大跨度为 366 天。
- 广告类型筛选编码不能为空白。
- 展示、点击、转化、花费均不得为负，且维持漏斗关系。
- 修改或删除投放数据只改变数据事实，不自动修改资金流水，避免历史数据修正造成隐式重复扣费。
- 已关联资金流水的投放记录不能换绑广告主，数据库同时保证流水账户与投放记录属于同一广告主。
- 若需要关联消费，使用独立消费接口的 `deliveryRecordId`/`businessNo` 显式关联。

### 代码结构

```text
delivery/
├── controller/
├── service/
├── mapper/
├── entity/
├── dto/request/
├── dto/response/
└── exception/
```

### 任务

- ✅ 实现实体、DTO、错误码和 Mapper。
- ✅ 实现单条录入和外部记录号幂等校验。
- ✅ 实现组合筛选、稳定排序和数据库物理分页。
- ✅ 实现详情、修正和受保护删除权限。
- ✅ 补充 Service、MockMvc 和 PostgreSQL 持久化测试。
- ✅ 补齐 OpenAPI 参数、成功响应和常见错误响应说明。

### 完成标准

- 能录入并查询至少 30 天、多个广告主和多个广告类型的模拟数据。
- 组合筛选和总数正确，分页不重复、不漏数。
- 重复入库、非法漏斗数据和越权修改均有明确错误码。

## 4.3 板块 C：统计与报表模块 `report`

### 目标

提供简化 BI 能力。所有比率都基于汇总后的分子、分母计算，不能简单平均各行比率。

### 指标口径

| 指标 | 计算方式 | 分母为 0 时 |
| --- | --- | --- |
| 展示量 | `SUM(impressions)` | `0` |
| 点击量 | `SUM(clicks)` | `0` |
| 转化量 | `SUM(conversions)` | `0` |
| 花费 | `SUM(spend)` | `0.00` |
| CTR | `SUM(clicks) / SUM(impressions)` | `0` |
| CVR | `SUM(conversions) / SUM(clicks)` | `0` |
| CPC | `SUM(spend) / SUM(clicks)` | `0` |

建议 SQL 使用 `NULLIF` 防止除零，接口层统一精度，例如比率保留 4 位、金额保留 2 位。

### 建议接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/reports/delivery/overview` | 指定条件下的指标总览 |
| `GET` | `/api/v1/reports/delivery/trend` | 按 `DAY/WEEK/MONTH` 返回时间趋势 |
| `GET` | `/api/v1/reports/delivery/by-advertiser` | 按广告主汇总、排序和分页 |
| `GET` | `/api/v1/reports/delivery/by-ad-type` | 按广告类型汇总 |

所有接口支持 `startDate`、`endDate`，并按场景支持可选的 `advertiserId`、`advertisingTypeCode`。`ADMIN`、`OPERATOR` 均可查询。

### SQL 实现原则

- 复杂聚合查询放入 `resources/mapper/report/*.xml`，便于审阅 SQL 和执行计划。
- 时间粒度使用 PostgreSQL `DATE_TRUNC`。
- 排名字段使用白名单枚举映射，不把请求中的排序字段直接拼接进 SQL。
- 广告主汇总需要分页时，数据查询和 `COUNT` 查询保持完全相同的过滤条件。
- 报表响应直接返回接口需要的投影 DTO，不先加载实体列表再在 Java 内存聚合。

### 任务

- ✅ 建立报表查询参数和统一指标响应模型。
- ✅ 实现指标总览 SQL 和接口。
- ✅ 实现日/周/月趋势 SQL 和接口。
- ✅ 实现广告主、广告类型维度 SQL 和接口。
- ✅ 测试日/周/月边界、空数据、除零、跨月和多维筛选。
- ✅ 用固定数据集校验 SQL 结果，避免只断言“接口返回 200”。
- ✅ 为慢查询执行 `EXPLAIN ANALYZE` 并记录执行计划摘要。

### 完成标准

- 四类报表接口均能通过 Swagger 演示。
- 固定测试数据的汇总值、CTR、CVR、CPC 与人工计算一致。
- 查询结果不因时区发生日期偏移；业务日期统一使用 `LocalDate`。

## 4.4 板块 D：账户与资金流水模块 `account`

> 当前状态：账户余额查询、原子消费、资金流水分页、并发保护、RBAC、OpenAPI 和三层测试均已完成。充值入账由板块 E 的模拟支付回调驱动，详见 `docs/sprint2-account-acceptance.md`。

### 目标

实现一个广告主一个资金账户，并保证余额、流水和业务操作在同一个数据库事务中一致。

### 建议接口

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/advertisers/{advertiserId}/account` | 查询余额 | `ADMIN`、`OPERATOR` |
| `GET` | `/api/v1/advertisers/{advertiserId}/account/transactions` | 分页查询流水 | `ADMIN`、`OPERATOR` |
| `POST` | `/api/v1/advertisers/{advertiserId}/account/consumptions` | 创建消费并扣减余额 | `ADMIN` |

账户建议在首次充值或首次查询时按明确策略创建；为方便理解和测试，推荐在创建广告主时同步创建零余额账户。需要为已有广告主在迁移或初始化服务中补建账户。

### 资金规则

- 流水金额始终保存正数，方向由 `RECHARGE`、`CONSUMPTION` 等交易类型表达。
- `businessNo` 唯一，相同业务号重复请求返回原结果或 409，但绝不能再次变更余额。
- 消费使用单条原子 SQL，例如按 `balance >= amount` 条件更新；受影响行数为 0 时区分账户不存在和余额不足。
- 余额更新与流水写入必须处于同一 `@Transactional` 事务，任一步失败全部回滚。
- 资金流水原则上只追加，不提供修改和删除接口。
- 流水保存 `balanceAfter`、操作者和可选业务关联，便于审计。

### 任务

- ✅ 实现账户查询和历史广告主账户初始化策略。
- ✅ 实现原子消费、余额不足判断和幂等业务号。
- ✅ 实现流水类型、时间范围的分页查询。
- ✅ 测试事务回滚、重复消费、并发消费和余额不为负。
- ✅ 消费日志记录业务号和 Request ID；充值订单日志及敏感信息规则由板块 E 延续。

### 完成标准

- 充值后余额和充值流水一致（待板块 E 回调入账完成后联合验收）。
- ✅ 消费后余额、消费流水和 `balanceAfter` 一致。
- ✅ 两个并发请求竞争同一笔余额时，不会发生超扣或负余额。

## 4.5 板块 E：模拟支付与回调模块 `payment`

### 目标

不接真实支付平台，但完整体现订单、签名、状态机、回调幂等和事务入账思路。

### 建议接口

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/payment-orders` | 创建充值订单 | `ADMIN` |
| `GET` | `/api/v1/payment-orders/{orderNo}` | 查询订单状态 | `ADMIN`、`OPERATOR` |
| `POST` | `/api/v1/payment-orders/{orderNo}/simulate` | 本地模拟支付成功/失败 | `ADMIN`，仅非生产环境 |
| `POST` | `/api/v1/payment-callbacks/mock` | 模拟支付方回调 | 无 JWT，必须验签 |

### 状态机

```text
PENDING -> SUCCESS
PENDING -> FAILED
PENDING -> CLOSED
```

终态订单不得回退。`SUCCESS` 回调再次到达时返回成功确认，但不得重复增加余额或重复生成充值流水。

### 回调处理顺序

1. 校验时间戳和 HMAC 签名，回调密钥从环境变量读取。
2. 使用支付方 `eventId` 判断是否已经处理。
3. 查询并锁定支付订单。
4. 校验订单号、广告主、金额和当前状态。
5. 将订单更新为成功。
6. 原子增加账户余额。
7. 写入唯一 `businessNo` 的充值流水。
8. 写入回调处理结果并提交事务。

### 任务

- [ ] 实现订单创建、查询和合法状态迁移。
- [ ] 实现仅在本地/测试环境启用的模拟支付入口。
- [ ] 实现 HMAC 验签、时间窗口校验和回调审计。
- [ ] 实现回调、订单更新、账户入账、流水落库的单事务处理。
- [ ] 测试签名错误、金额不符、订单不存在、重复回调和并发回调。

### 完成标准

- 一笔充值订单只能产生一笔充值流水。
- 同一回调重复提交多次，余额只增加一次。
- 非法签名和金额篡改不会改变订单、余额或流水。

## 4.6 板块 F：性能、测试、文档与 Demo

### 索引计划

先根据实际 SQL 编写索引，再使用执行计划验证，避免为了“有索引”而重复建索引。

建议候选索引：

```sql
CREATE UNIQUE INDEX uk_delivery_external_record_no
    ON advertising_delivery_records (external_record_no);

CREATE INDEX idx_delivery_record_date
    ON advertising_delivery_records (record_date);

CREATE INDEX idx_delivery_advertiser_date
    ON advertising_delivery_records (advertiser_id, record_date);

CREATE INDEX idx_delivery_ad_type_date
    ON advertising_delivery_records (advertising_type_id, record_date);

CREATE UNIQUE INDEX uk_accounts_advertiser_id
    ON advertiser_accounts (advertiser_id);

CREATE UNIQUE INDEX uk_account_transactions_business_no
    ON advertiser_account_transactions (business_no);

CREATE INDEX idx_account_transactions_account_created
    ON advertiser_account_transactions (advertiser_account_id, created_at DESC);

CREATE UNIQUE INDEX uk_payment_orders_order_no
    ON recharge_orders (order_no);

CREATE UNIQUE INDEX uk_payment_callbacks_event_id
    ON recharge_payment_callbacks (provider_event_id);
```

最终索引以 `EXPLAIN ANALYZE` 结果为准。小数据量下 PostgreSQL 选择顺序扫描是正常现象，验证时需要准备足够的模拟数据，不能仅凭是否出现 `Index Scan` 判断性能。

### 高频接口优化原则

- 明细查询强制分页并限制最大页大小。
- 报表强制日期范围，避免无界聚合。
- 只查询响应需要的列，聚合直接返回 DTO。
- 避免 N+1 查询；广告主和广告类型名称通过一次 JOIN 返回。
- 第一阶段优先优化 SQL 和索引；只有测量到重复热点查询后才启用 Caffeine。
- 如果增加缓存，TTL 建议 30～60 秒，并在投放数据写入/修改/删除后主动失效相关统计缓存。

### 测试分层

- 单元测试：业务校验、状态机、计算和错误码。
- MockMvc：参数校验、统一响应、JWT 和 RBAC。
- PostgreSQL 集成测试：Flyway、聚合 SQL、约束、原子扣款和锁。
- 并发测试：重复回调、重复消费和余额竞争。
- 冒烟测试：按 Demo 脚本跑完整业务链路。

### Demo 场景

准备 3 个广告主、4 种广告类型和至少 30 天投放记录，按以下顺序演示：

1. 登录并获取管理员 Token。
2. 查询投放明细并演示日期、广告主、类型组合筛选。
3. 查看总览、日趋势、广告主排名和广告类型分布。
4. 创建一笔充值订单，调用模拟支付并触发成功回调。
5. 查看余额和充值流水。
6. 使用唯一业务号发起消费，查看扣款后的余额和消费流水。
7. 重复发送同一支付回调，证明余额未二次增加。
8. 发起超过余额的消费，展示明确的业务错误。
9. 在 pgAdmin 运行核心统计 SQL 和 `EXPLAIN ANALYZE`，验证接口结果及索引设计。

### 产出文档

- `docs/sprint2.md`：本开发计划。
- `docs/sprint2-database-design.md`：表结构、约束、核心 SQL、索引和执行计划说明。
- `docs/sprint2-demo.md`：初始化数据、Swagger 演示步骤和预期结果。
- `docs/sprint2-test-report.md`：测试数量、覆盖场景、结果和已知限制。

## 5. 建议开发顺序和 5 日节奏

| 时间 | 主任务 | 当日可验收结果 |
| --- | --- | --- |
| Day 1 | 基线恢复、ER、`V3/V4` 迁移、实体与枚举 | 数据库从 `V2` 升级成功，约束测试通过 |
| Day 2 | `delivery` 入库、查询、分页和权限 | 投放数据 CRUD/查询可通过 Swagger 演示 |
| Day 3 | `report` 聚合 SQL 和四类报表 | 固定数据集统计结果与人工计算一致 |
| Day 4 | `account` 余额、流水、原子消费 | 充值/消费流水一致，并发消费不超扣 |
| Day 5 | `payment` 回调、性能、文档和完整 Demo | 重复回调不重复入账，全量测试和演示通过 |

如果实际周期超过 5 天，建议把 Day 4、Day 5 各拆为两天，不要通过减少资金一致性测试压缩工期。

## 6. 分支与任务建议

建议建立 Sprint 集成分支：

```text
main
└── sprint2-backend-development
    ├── feature/sprint2-database
    ├── feature/delivery-data
    ├── feature/delivery-report
    ├── feature/account-ledger
    ├── feature/mock-payment
    └── test/sprint2-acceptance
```

推荐合并顺序：数据库 -> 投放数据 -> 报表 -> 账户流水 -> 模拟支付 -> 验收。每个功能分支必须包含对应测试和 OpenAPI 文档后再合入 Sprint 2 集成分支。

## 7. Definition of Done

每个模块只有同时满足以下条件才算完成：

- [ ] 代码遵循现有模块分包和统一响应规范。
- [ ] 请求参数有校验，业务失败有模块化错误码和正确 HTTP 状态。
- [ ] 权限已在 `SecurityConfig` 和 MockMvc 测试中体现。
- [ ] 数据库规则同时有迁移约束和持久化测试。
- [ ] 涉及余额、订单或流水的多步写操作具有明确事务边界。
- [ ] Swagger 中包含接口说明、参数和主要错误响应。
- [ ] `scripts/test.cmd` 在 PostgreSQL 启动的环境下全量通过。
- [ ] README 增加 Sprint 2 启动、接口和 Demo 文档链接。
- [ ] 没有把支付密钥、JWT、密码或敏感回调内容提交到仓库或写入普通日志。

## 8. Sprint 验收清单

### 功能验收

- [ ] 投放数据可以新增、筛选、分页和查看详情。
- [ ] 时间、广告主、广告类型统计均可返回正确结果。
- [ ] 账户余额、充值流水、消费流水可以查询。
- [ ] 模拟支付成功后订单、余额和流水一致。
- [ ] 重复回调、重复消费不会重复记账。
- [ ] 余额不足、非法金额和非法状态迁移会被拒绝。

### 技术验收

- [ ] Flyway 从空库和现有 `V2` 数据库均可迁移。
- [ ] 核心 SQL、公式和索引理由有文档。
- [ ] 高频查询有 `EXPLAIN ANALYZE` 记录。
- [ ] 全量自动化测试通过。
- [ ] Demo 数据和演示步骤可以由其他人重复执行。

### 导师要求映射

| 导师要求 | Sprint 2 对应产出 |
| --- | --- |
| 投放数据入库设计、查询接口 | 板块 A、B |
| 时间/广告主/广告类型统计 | 板块 C |
| 简化版 BI 报表 | 四类报表 API + Swagger/可选图表 Demo |
| 账户余额、充值和消费流水 | 板块 D |
| 模拟支付、回调逻辑 | 板块 E |
| 索引和高频接口优化 | 板块 F |
| 数据模块 + 账户模块代码 | `delivery`、`report`、`account`、`payment` |
| 核心 SQL 设计说明 | `docs/sprint2-database-design.md` |
| 数据统计 Demo 展示 | `docs/sprint2-demo.md` 和 Swagger 演示 |

## 9. 主要风险与处理方式

| 风险 | 影响 | 处理方式 |
| --- | --- | --- |
| 统计口径不一致 | Demo 数字无法解释 | 先固定公式，用同一固定数据集做 SQL 和人工双重校验 |
| 投放日期与时区混用 | 趋势跨日偏移 | 业务日期使用 `DATE/LocalDate`，审计时间使用 UTC `TIMESTAMPTZ` |
| 并发扣款产生负余额 | 资金数据错误 | 条件原子更新或行锁，同事务写流水，并增加并发测试 |
| 重复回调重复充值 | 严重资金错误 | `eventId`、订单状态、流水业务号三层幂等保护 |
| 为报表提前引入复杂缓存 | 增加失效和一致性问题 | 先测 SQL/索引，缓存仅作为有测量依据的扩展项 |
| Sprint 范围过大 | 核心链路无法按时验收 | CSV、导出、退款、真实支付和图表前端均放到扩展项 |
