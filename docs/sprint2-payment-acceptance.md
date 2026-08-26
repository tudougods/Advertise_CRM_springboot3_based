# Sprint 2 板块 E：模拟支付与回调模块验收记录

> 验收日期：2026-08-27
>
> 验收结论：E1～E5 已完成。充值订单、状态机、本地模拟、HMAC 回调、审计、原子入账、并发幂等、权限和 OpenAPI 契约均通过自动化验收。

## 1. 接口与权限

| 方法 | 路径 | 能力 | 权限 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/payment-orders` | 创建 `PENDING` 充值订单 | `ADMIN` |
| `GET` | `/api/v1/payment-orders/{orderNo}` | 查询订单状态 | `ADMIN`、`OPERATOR` |
| `POST` | `/api/v1/payment-orders/{orderNo}/simulate` | 模拟成功或失败 | `ADMIN`，仅 `local/test` |
| `POST` | `/api/v1/payment-callbacks/mock` | 接收支付方回调 | 无 JWT，必须通过 HMAC 验签 |

三组 MockMvc 安全测试验证了 401、403、角色权限和统一错误响应。OpenAPI 自动化测试确认四个端点、Bearer 安全边界、回调请求体及两个必填验签请求头均出现在 `/v3/api-docs`。

## 2. 状态机与回调安全

- 订单仅允许从 `PENDING` 进入 `SUCCESS`、`FAILED` 或 `CLOSED`，终态不可回退。
- `/simulate` Controller 只在 `local/test` profile 注册，生产 profile 不暴露该入口。
- 回调签名原文为 `timestamp + "." + HTTP 原始请求体`，算法为 HMAC-SHA256。
- 签名头格式为 `sha256=<64 位十六进制摘要>`，使用常量时间比较。
- 回调密钥由 `MOCK_PAYMENT_CALLBACK_SECRET` 提供；默认时间窗口为 300 秒，可通过 `MOCK_PAYMENT_CALLBACK_TOLERANCE_SECONDS` 调整。
- Controller 对原始请求体最多读取 16 KiB + 1 字节，超限请求不会进入 Service；通过大小检查后先验签再解析 JSON，非法签名不会生成审计记录。
- 已验签但订单号、广告主或金额不匹配的回调会留下 `REJECTED` 审计记录，不改变资金数据。

## 3. 原子入账与幂等

成功回调在一个数据库事务中完成：锁定订单、更新为 `SUCCESS`、原子增加余额、写入唯一 `RECHARGE` 流水、更新回调为 `PROCESSED`。任一步骤失败都会整体回滚。

幂等保护包括：

- 支付方 `eventId` 唯一，重复事件不会重复处理；相同事件携带不同原文时返回冲突。
- 仅 `PROCESSED` 回调返回幂等成功；`REJECTED` 重试保持原错误，遗留 `RECEIVED` 状态返回处理冲突。
- 订单行锁串行化同一订单的并发回调或本地模拟。
- 每个充值订单最多关联一条充值流水，数据库唯一约束提供最终保护。
- 订单、账户和流水的广告主归属由数据库约束再次校验。
- `V10` 要求成功订单必须同时具有支付时间和平台交易号，非成功订单不得保留这些字段。
- 失败回调只将订单更新为 `FAILED` 并完成审计，不增加余额、不创建流水。

## 4. 自动化验收

执行命令：

```powershell
.\mvnw.cmd test
```

板块 E 测试覆盖：

- 订单号生成、订单创建/查询、金额边界和 PostgreSQL 持久化。
- 合法与非法状态迁移、本地/生产 profile 隔离。
- 时间窗口、签名格式、密钥配置、原始请求体限制和回调审计。
- 成功/失败入账、重复事件、重复订单、金额篡改和缺失订单。
- 并发回调只入账一次，以及流水冲突时整个充值事务回滚。
- ADMIN/OPERATOR/匿名访问矩阵和 OpenAPI 契约。

全量回归结果：`390/390` 通过，0 失败，0 错误，0 跳过。

## 5. 模块提交

| 模块 | Commit | 内容 |
| --- | --- | --- |
| E1 | `870b35d` | 充值订单创建与查询 |
| E2 | `333f0a3` | 状态机与本地模拟支付 |
| E3 | `5539496` | HMAC 回调验签与审计 |
| E4 | `80d53f6` | 单事务原子充值与并发幂等 |
| E5 | `1ee9bb8` | 权限、OpenAPI 与验收收口 |
| E review | 本次提交 | 请求边界、重放语义、V10 约束与测试补强 |

板块 F 将继续负责执行计划、索引验证、完整 Demo、最终测试报告和 Sprint 2 总验收；不在板块 E 重复增加性能或演示代码。
