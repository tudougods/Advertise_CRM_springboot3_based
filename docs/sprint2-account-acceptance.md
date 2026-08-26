# Sprint 2 板块 D：账户与资金流水模块验收记录

> 验收日期：2026-08-26
>
> 验收结论：D1～D4 的账户侧能力已经完成。余额查询、原子消费、不可变流水、分页查询、并发保护和接口文档均通过自动化验收；充值入账由板块 E 的模拟支付回调继续实现。

## 1. 已实现接口

| 方法 | 路径 | 能力 | 权限 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/advertisers/{advertiserId}/account` | 查询账户余额 | `ADMIN`、`OPERATOR` |
| `POST` | `/api/v1/advertisers/{advertiserId}/account/consumptions` | 原子扣减余额并创建消费流水 | `ADMIN` |
| `GET` | `/api/v1/advertisers/{advertiserId}/account/transactions` | 按类型、时间范围物理分页查询流水 | `ADMIN`、`OPERATOR` |

三个接口均使用统一的 `ApiResponse<T>`；流水列表额外使用 `PageResponse<T>`。OpenAPI 自动化测试确认三个路径均出现在 `/v3/api-docs`，资金流水没有暴露新增通用流水、修改或删除接口。

## 2. 账户生命周期

- `V4` 为已有广告主补建零余额账户。
- 创建广告主时在同一事务中同步创建零余额账户，一个广告主只能有一个账户。
- 余额查询是只读操作，不会在 `GET` 请求中隐式补建账户。
- 广告主不存在与广告主存在但账户缺失使用不同错误码，便于识别数据一致性问题。
- 没有业务历史的零余额账户可随广告主删除；存在投放、流水或充值历史时保留审计数据。

## 3. 原子消费与一致性

消费使用以下单条 PostgreSQL 语句完成余额检查和扣减：

```sql
UPDATE advertiser_accounts
SET balance = balance - :amount,
    updated_at = CURRENT_TIMESTAMP
WHERE id = :account_id
  AND balance >= :amount
RETURNING balance;
```

核心规则：

- 流水金额始终是两位小数的正数，方向由 `CONSUMPTION` 表达。
- 原子扣款和消费流水插入位于同一个 `@Transactional` 事务，任何后续校验失败都会回滚扣款。
- `businessNo` 全局唯一；写入使用 `ON CONFLICT (business_no) DO NOTHING` 处理并发重复请求。
- 当并发重复请求耗尽余额时会再次检查业务号，稳定返回业务号冲突，而不是误报余额不足。
- 可选的投放记录按照“账户行 → 投放记录行”的顺序加锁，并校验与账户属于同一广告主。
- `V6`、`V7` 的触发器和行锁为绕过 Service 的直接 SQL 及并发竞态提供最终保护。
- 流水保存 `balanceAfter`、操作用户、业务号、备注及可选投放记录，写入后不提供修改或删除能力。
- 成功日志记录经过控制字符清理的 `businessNo`；Request ID 由统一请求过滤器写入 MDC。

## 4. 流水分页规则

流水查询支持：

- `transactionType`：`RECHARGE` 或 `CONSUMPTION`。
- `startTime`、`endTime`：必须成对提供，首尾时间均包含，跨度最多 366 天。
- `page`：从 1 开始。
- `size`：默认 20，最大 100。

查询在 PostgreSQL 中完成筛选、`COUNT` 和物理分页，按 `created_at DESC, id DESC` 稳定排序。现有索引 `idx_account_transactions_account_created (advertiser_account_id, created_at DESC)` 与账户过滤及主要排序前缀匹配，因此 D4 不新增重复索引。

## 5. 主要错误响应

| HTTP 状态 | 错误码 | 场景 |
| --- | --- | --- |
| `400` | `COMMON_VALIDATION_ERROR` | 消费业务号、金额、流水类型或分页参数不合法 |
| `400` | `ACCOUNT_INCOMPLETE_TRANSACTION_TIME_RANGE` | 只提供流水开始时间或结束时间 |
| `400` | `ACCOUNT_INVALID_TRANSACTION_TIME_RANGE` | 开始时间晚于结束时间 |
| `400` | `ACCOUNT_TRANSACTION_TIME_RANGE_TOO_LARGE` | 流水时间跨度超过 366 天 |
| `404` | `ACCOUNT_ADVERTISER_NOT_FOUND` | 广告主不存在 |
| `404` | `ACCOUNT_NOT_FOUND` | 广告主存在但账户缺失 |
| `404` | `ACCOUNT_DELIVERY_RECORD_NOT_FOUND` | 关联的投放记录不存在 |
| `409` | `ACCOUNT_BUSINESS_NO_ALREADY_EXISTS` | 业务号已完成或发生并发重复请求 |
| `409` | `ACCOUNT_INSUFFICIENT_BALANCE` | 账户余额不足 |
| `409` | `ACCOUNT_DELIVERY_RECORD_ADVERTISER_MISMATCH` | 投放记录与账户不属于同一广告主 |

认证失败、权限不足、枚举转换和分页参数错误继续复用项目统一错误响应。

Service 仍使用 `ACCOUNT_INVALID_BUSINESS_NO` 和 `ACCOUNT_INVALID_AMOUNT` 对绕过 Controller 校验的内部调用进行防御，但公开 HTTP 接口会优先返回统一参数校验错误。

## 6. 自动化验收结果

执行命令：

```powershell
.\mvnw.cmd test
```

验收结果：A～D review 修复后项目全量测试 `318/318` 通过，0 失败，0 错误，0 跳过。D1～D4 新增的账户专项测试共 `39/39` 通过：

| 范围 | 测试数 | 主要覆盖 |
| --- | ---: | --- |
| D1 余额查询 | 8 | DTO 映射、账户缺失、ADMIN/OPERATOR、401 和参数校验 |
| D2 原子消费 | 18 | 余额不足、事务回滚、重复业务号、投放关联、RBAC 和两类真实并发竞争 |
| D3 流水分页 | 12 | 类型/时间筛选、错误边界、物理分页、总数和稳定排序 |
| D4 OpenAPI | 1 | 三个端点可演示且流水保持只追加契约 |

此外，板块 A 已有的账户数据库测试继续验证账户唯一、余额非负、流水金额、业务号唯一、外键和删除保护。

## 7. 提交记录

| 模块 | Commit | 内容 |
| --- | --- | --- |
| D1 | `4903ef1` | 广告主账户余额查询 |
| D2 | `315ad6a` | 原子消费、幂等和并发事务测试 |
| D3 | `0c481d5` | 资金流水筛选与物理分页 |
| D4 | 本次提交 | 整体 review、OpenAPI 验收和文档收口 |

## 8. 与板块 E 的边界

板块 D 不直接伪造充值流水。`RECHARGE` 流水将在板块 E 验证模拟支付回调后，由订单成功、账户加款、充值流水和回调审计组成的单一事务生成。

因此，板块 D 当前已完成消费侧和通用流水查询能力；“充值后余额与充值流水一致”将在板块 E 完成后联合验收，不能通过临时管理接口绕过支付回调状态机。
