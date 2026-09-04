# Advertiser CRM Backend

Advertiser CRM 是一个面向广告业务后台的模块化单体服务，覆盖内部用户与权限、广告主档案、广告投放记录、统计报表、广告主账户、消费流水、充值订单和模拟支付回调。项目以统一 API 契约、PostgreSQL 事务约束和可重复自动化测试保证各模块行为一致。

## 主要功能

| 模块 | 能力 | 主要权限 |
| --- | --- | --- |
| 认证与用户 | 注册申请、登录、JWT、用户角色和状态管理、认证限流 | 注册和登录公开；用户管理仅 ADMIN |
| 广告主 | 广告主、分类、负责人关系和启停状态 | 查询允许 ADMIN/OPERATOR；维护仅 ADMIN |
| 广告投放 | 广告类型字典、投放记录录入、组合筛选、分页、修正和删除 | 查询与录入允许 ADMIN/OPERATOR；修正和删除仅 ADMIN |
| 统计报表 | 总览、日/周/月趋势、按广告主和广告类型聚合 | ADMIN/OPERATOR |
| 账户与消费 | 广告主余额、原子扣款、不可变资金流水和历史分页 | 查询允许 ADMIN/OPERATOR；消费仅 ADMIN |
| 充值与支付 | 充值订单、订单状态机、签名回调、幂等审计和本地支付模拟 | 创建和模拟仅 ADMIN；查询允许 ADMIN/OPERATOR |

本地模拟支付入口只在 local 或 test Profile 注册，默认容器运行方式不会暴露该入口。支付回调端点会执行 HMAC 验签、时间窗口检查和幂等处理。

## 技术栈

- Java 21
- Spring Boot 3.5.16
- Spring Security、BCrypt、JWT
- MyBatis-Plus 3.5.17、MyBatis XML
- PostgreSQL 16、Flyway
- SpringDoc OpenAPI 2.8.17
- Maven Wrapper、JUnit 5、Mockito、Testcontainers
- Docker、Docker Compose

## 项目结构

业务代码按模块组织，每个模块内部再按 Controller、Service、Mapper、Entity 和 DTO 分层：

~~~text
src/main/java/com/internship/crm/
├─ auth/          认证、JWT、限流和 Security 接入
├─ user/          用户、角色和账号状态
├─ advertiser/    广告主、分类和负责人关系
├─ delivery/      广告类型和投放记录
├─ report/        投放统计与趋势报表
├─ account/       账户、消费和资金流水
├─ payment/       充值订单、模拟支付和回调审计
├─ common/        统一响应、异常和 Request ID
└─ config/        Spring、MyBatis、OpenAPI 和时间配置
~~~

完整的目录职责、模块边界和文件放置规则见 [项目结构说明](docs/project-structure.md)。

## 环境要求

- JDK 21；运行 Maven 命令时无需单独安装 Maven。
- Docker Desktop 或兼容的 Docker Engine，并启用 Docker Compose。
- Windows 示例使用 PowerShell；macOS/Linux 可将 mvnw.cmd 换为 ./mvnw。

确认环境：

~~~powershell
java -version
docker version
docker compose version
.\mvnw.cmd -version
~~~

## 配置准备

首次克隆后复制配置模板：

~~~powershell
Copy-Item .env.example .env
~~~

至少修改以下值，不能继续使用 change_me，也不要提交 .env：

| 变量 | 用途 | 规则或默认值 |
| --- | --- | --- |
| POSTGRES_DB | 业务数据库名 | 默认 advertiser_crm |
| POSTGRES_USER | 数据库用户 | 默认 crm_user |
| POSTGRES_PASSWORD | 数据库密码 | 必须改为本地强密码 |
| POSTGRES_PORT | PostgreSQL 本机端口 | 默认 15432 |
| JWT_SECRET | JWT HMAC 签名密钥 | Base64，解码后至少 32 字节 |
| JWT_EXPIRATION_MINUTES | JWT 有效期 | 默认 60 分钟 |
| MOCK_PAYMENT_CALLBACK_SECRET | 支付回调 HMAC 密钥 | Base64，解码后至少 32 字节；不得与 JWT 密钥相同 |
| BUSINESS_TIME_ZONE | 默认日期范围使用的业务时区 | 默认 UTC，也可使用 Australia/Sydney 等 IANA 时区 |
| SERVER_PORT | 应用本机端口 | 默认 8080 |
| PGADMIN_DEFAULT_EMAIL | pgAdmin 登录邮箱 | 仅本地管理页面使用 |
| PGADMIN_DEFAULT_PASSWORD | pgAdmin 登录密码 | 必须改为本地强密码 |
| PGADMIN_PORT | pgAdmin 本机端口 | 默认 5150 |

在 PowerShell 中生成两个不同的 32 字节 Base64 密钥：

~~~powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
~~~

把两次结果分别写入 JWT_SECRET 和 MOCK_PAYMENT_CALLBACK_SECRET。仓库中的值都是占位符，不包含真实密码或密钥。

## 本地开发运行

1. 启动 PostgreSQL 和 pgAdmin：

~~~powershell
docker compose up -d postgres pgadmin
docker compose ps
~~~

PostgreSQL 只绑定到 127.0.0.1:15432，pgAdmin 默认位于 <http://localhost:5150>。pgAdmin 已预置 Local Development / Advertiser CRM Local 连接，首次连接输入 .env 中的 POSTGRES_PASSWORD。

2. 使用 local Profile 启动应用，以启用本地模拟支付接口：

~~~powershell
.\mvnw.cmd "-Dspring-boot.run.arguments=--spring.profiles.active=local" spring-boot:run
~~~

应用启动时 Flyway 自动执行 src/main/resources/db/migration 中尚未执行的迁移。不要修改已经执行过的迁移文件；数据库变更应新增更高版本。

3. 检查入口：

- 健康检查：<http://localhost:8080/actuator/health>
- Swagger UI：<http://localhost:8080/swagger-ui.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>

停止本地基础服务：

~~~powershell
docker compose down
~~~

docker compose down 会保留数据库卷。只有明确需要清空全部本地数据时才使用 docker compose down -v。

## 容器部署与运行

项目根目录的多阶段 Dockerfile 会先使用 JDK 21 构建 JAR，再用 JRE 21 和非 root 用户运行。完整 Compose 会启动 PostgreSQL、pgAdmin 和应用：

~~~powershell
docker compose --profile full up --build -d
docker compose --profile full ps
docker compose logs -f app
~~~

Compose 会把 JWT_SECRET、数据库配置和回调密钥显式传入应用容器，不会把本机 .env 文件复制进镜像。默认容器未激活 local Profile，因此 POST /api/v1/payment-orders/{orderNo}/simulate 不会注册；这是部署时的安全边界。

部署后验证：

~~~powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-WebRequest http://localhost:8080/v3/api-docs -UseBasicParsing
~~~

停止服务：

~~~powershell
docker compose --profile full down
~~~

生产或共享环境应另外使用外部密钥管理、TLS、可信反向代理、数据库备份和独立 PostgreSQL，不应继续使用仓库示例凭据或把 pgAdmin 暴露到公网。

## 数据库初始化与首个管理员

查看表和 Flyway 历史：

~~~powershell
docker compose exec postgres psql -U crm_user -d advertiser_crm -c "\dt"
docker compose exec postgres psql -U crm_user -d advertiser_crm -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
~~~

项目不内置默认管理员密码。首次初始化时：

1. 调用 POST /api/v1/auth/register 注册账号；新账号固定为 PENDING OPERATOR。
2. 仅在受控的本地初始化阶段，通过 pgAdmin Query Tool 或 psql 执行：

~~~sql
UPDATE users
SET role = 'ADMIN',
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE LOWER(username) = LOWER('替换为刚注册的用户名');
~~~

3. 调用 POST /api/v1/auth/login 获取 accessToken，在 Swagger 的 **Authorize** 对话框中填写 Bearer Token。

系统会阻止降级、禁用或删除最后一个 ACTIVE ADMIN。共享或正式环境应把管理员初始化做成受审计的运维流程。

## API 约定

- 业务响应统一使用 ApiResponse<T>，包含 success、code、message、data、timestamp 和 requestId。
- 每个请求返回 X-Request-ID。客户端提供的 ID 只能包含字母、数字、点、下划线和短横线，长度不超过 64。
- 列表接口使用数据库物理分页；默认第 1 页、每页 20 条，size 最大为 100。
- 400、401、403、404、409、415、429 和 500 等错误均使用统一响应，不向客户端泄露堆栈、SQL、路径、密码或 Token。
- 请求完成日志包含 Request ID、方法、路径、状态和耗时；日志不记录请求体、认证头或查询参数。

全部接口和请求字段以运行中的 Swagger/OpenAPI 为准。

## 测试

Docker 运行时执行：

~~~powershell
.\scripts\test.cmd
~~~

脚本会使用 Testcontainers 启动一次性 PostgreSQL 16，不读写日常开发数据库 advertiser_crm。它逐项输出中文测试结果；失败时补充完整 Maven/Spring 日志。也可以使用标准命令：

~~~powershell
.\mvnw.cmd test
~~~

性能复现实验必须在独立数据库执行，步骤与结果见 [Sprint 3 B1 性能记录](docs/sprint3-B1-performance.md)。

## 相关文档

- [项目结构说明](docs/project-structure.md)
- [数据库设计](docs/database-design.md)
- [Sprint 1 开发计划](docs/spring1.md)
- [Sprint 2 开发计划](docs/sprint2.md)
- [Sprint 2 最终测试报告](docs/sprint2-F-test-report.md)
- [Sprint 3 开发计划](docs/spring3.md)
- [Sprint 3 B1 核心接口性能记录](docs/sprint3-B1-performance.md)
- [Sprint 3 B2 日志分级与安全检查](docs/sprint3-B2-logging.md)
- [后端系统总结与优化报告（Sprint 1—Sprint 3，DOCX）](docs/后端系统总结与优化报告.docx)
- [Sprint 3 总结与优化过程记录（Markdown）](docs/sprint3-optimization-report.md)
