# Sprint 2 完整 Demo

> 验收日期：2026-08-27
>
> 验收环境：PostgreSQL 16、Flyway V10、Spring `local` Profile

## 1. Demo 目标

通过真实 HTTP API 演示 Sprint 2 的完整业务链路：

1. 管理员登录并读取 OpenAPI。
2. 创建 3 个广告主，并确认账户自动创建且初始余额为零。
3. 使用 4 种广告类型录入连续 30 天投放数据。
4. 查询投放明细、总览、趋势、广告主排名和广告类型分布。
5. 分别通过本地模拟支付和 HMAC 回调完成两笔充值。
6. 重发同一回调，确认不会重复入账。
7. 关联投放记录发起消费，并查询充值、消费流水。
8. 发起超过余额的消费，确认返回明确错误且余额不变。

Demo 不依赖 F1 的批量性能数据；建议使用独立临时数据库运行。

## 2. 环境准备

启动 PostgreSQL 后创建隔离数据库：

```powershell
docker exec advertiser-crm-postgres-1 createdb -U crm_user -O crm_user advertiser_crm_f2
```

准备本地密钥：

```powershell
$env:MOCK_PAYMENT_CALLBACK_SECRET = "<Base64 编码的至少 32 字节随机密钥>"
```

回调密钥必须是 Base64；解码后少于 32 字节会返回 `PAYMENT_CALLBACK_CONFIGURATION_ERROR`。

用同一个回调密钥启动应用：

```powershell
.\mvnw.cmd "-Dspring-boot.run.arguments=--server.port=18080 --spring.profiles.active=local --spring.datasource.url=jdbc:postgresql://localhost:15432/advertiser_crm_f2 --security.jwt.secret=<Base64 JWT 密钥> --app.payment.callback-secret=$env:MOCK_PAYMENT_CALLBACK_SECRET" spring-boot:run
```

启动成功时，Flyway 应显示从空 Schema 连续应用 V1～V10，健康检查返回 `UP`。

## 3. 初始化本地管理员

公开注册只创建 `PENDING OPERATOR`。先通过 Swagger 调用 `POST /api/v1/auth/register`：

```json
{
  "username": "sprint2_demo_admin",
  "password": "<本轮临时管理员密码>",
  "displayName": "Sprint 2 Demo Admin",
  "email": "sprint2_demo_admin@example.com"
}
```

再按项目既有本地初始化规则，在临时数据库中激活该账号：

```sql
UPDATE users
SET role = 'ADMIN', status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
WHERE username = 'sprint2_demo_admin' AND status = 'PENDING';
```

此 SQL 只用于空本地数据库的首位管理员初始化，不属于生产部署流程。

## 4. API 冒烟顺序

1. `GET /actuator/health`：确认状态为 `UP`。
2. `POST /api/v1/auth/login`：使用本地管理员登录，取得临时 JWT。
3. `GET /v3/api-docs`：确认支付、账户、投放和报表路径存在。
4. `GET /api/v1/advertising-types`：确认预置 `SEARCH`、`DISPLAY`、`VIDEO`、`SOCIAL`。
5. `POST /api/v1/advertisers`：创建 3 个广告主。
6. `GET /api/v1/advertisers/{id}/account`：确认三个账户余额均为 `0.00`。
7. `POST /api/v1/delivery-records`：录入连续 30 天数据，广告主和类型轮换使用。
8. 查询投放明细以及 `/overview`、`/trend`、`/by-advertiser`、`/by-ad-type` 四类报表。
9. 创建 `1000.00` 充值订单，通过 `/{orderNo}/simulate` 完成本地模拟支付。
10. 创建 `500.00` 充值订单；Base64 解码回调密钥后，按 `timestamp + "." + UTF-8 原始请求体` 生成 HMAC-SHA256，调用公开回调。时间戳和小写十六进制摘要分别放入 `X-Mock-Payment-Timestamp`、`X-Mock-Payment-Signature: sha256=<摘要>`。
11. 使用完全相同的请求体、时间戳和签名重发回调，确认 `duplicate=true`。
12. 关联第一条投放记录消费 `300.00`，查询余额和资金流水。
13. 再消费 `2000.00`，确认 HTTP 409 和 `ACCOUNT_INSUFFICIENT_BALANCE`。

整个流程使用真实 HTTP API；JWT、管理员密码和回调密钥均不写入文档或验收结果。

## 5. 本轮验收结果

| 场景 | 结果 | 核心断言 |
| --- | --- | --- |
| 健康检查、登录、OpenAPI | 通过 | `UP`、ADMIN JWT、支付 API 存在 |
| 广告主与账户 | 通过 | 3 个广告主、3 个零余额账户 |
| 投放数据 | 通过 | 4 种广告类型、连续 30 天、每个广告主 10 条 |
| 投放查询与报表 | 通过 | 明细筛选、总览、30 个趋势点、3 个广告主、4 种类型 |
| 本地模拟充值 | 通过 | 订单成功，入账 `1000.00` |
| HMAC 回调充值 | 通过 | 订单成功，入账 `500.00`，回调审计为 `PROCESSED` |
| 重复回调 | 通过 | 返回 `duplicate=true`，余额仍为 `1500.00` |
| 消费与流水 | 通过 | 消费 `300.00`，余额为 `1200.00`，共 3 条资金流水 |
| 超余额消费 | 通过 | HTTP 409、`ACCOUNT_INSUFFICIENT_BALANCE`，余额保持 `1200.00` |

数据库最终交叉核对：

```text
Flyway V10
用户 1，广告主 3，账户 3，投放记录 30
成功充值订单 2，已处理回调审计 1
资金流水 3：充值 2，消费 1
账户余额合计 1200.00
```

## 6. Swagger 手工演示顺序

需要现场演示时，可在 `http://localhost:18080/swagger-ui.html` 按以下顺序操作：

1. 登录并在 Swagger 中 Authorize。
2. 查询广告类型和广告主账户。
3. 查询投放明细以及四类投放报表。
4. 查询两笔成功充值订单。
5. 查询余额和三条资金流水。
6. 展示重复回调未重复入账的结果。
7. 展示超余额消费的 409 错误。

## 7. 清理

停止应用后删除隔离数据库：

```powershell
docker exec advertiser-crm-postgres-1 dropdb -U crm_user advertiser_crm_f2
```

清理只作用于明确命名的 F2 临时数据库，不修改日常开发数据库 `advertiser_crm`。
