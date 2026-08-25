# Advertiser CRM Backend

广告商 CRM 后端原型的 Spring Boot 模块化单体项目。当前已包含 Sprint 1 核心表迁移、公共 Web 规范、用户认证与权限，以及广告主档案、状态和分类管理代码。

## 技术基线

- Java 21
- Spring Boot 3.5.16
- Spring Security
- MyBatis-Plus 3.5.17
- PostgreSQL 16
- Flyway
- SpringDoc OpenAPI 2.8.17
- Maven Wrapper

## 本地启动

启动 PostgreSQL 和 pgAdmin：

```powershell
docker compose up -d postgres pgadmin
```

默认将容器的 PostgreSQL `5432` 端口映射到本机 `15432`，避免与本机已有 PostgreSQL 服务冲突。

浏览器打开 http://localhost:5150 进入 pgAdmin。登录邮箱和密码分别读取项目根目录 `.env` 中的 `PGADMIN_DEFAULT_EMAIL` 和 `PGADMIN_DEFAULT_PASSWORD`，真实凭据不写入仓库。pgAdmin 端口只绑定本机地址，局域网中的其他设备无法直接访问。

登录后展开 `Local Development`，选择预置的 `Advertiser CRM Local`。首次连接时请输入项目根目录 `.env` 中的 `POSTGRES_PASSWORD`。首次克隆项目时，先将 `.env.example` 复制为 `.env`，再替换其中的 `change_me`；不要提交 `.env`。

pgAdmin 登录凭据只用于进入管理页面；PostgreSQL 凭据用于连接业务数据库，两者相互独立。首次创建 pgAdmin 数据卷后，修改环境变量不会自动重置已有的 pgAdmin 管理员密码。

首次复制 `.env.example` 后，还需要为本机生成 JWT 签名密钥：

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

把命令生成的值填入本机 `.env` 的 `JWT_SECRET`。该值不得提交到 Git；不同开发环境应使用不同密钥。然后启动应用：

```powershell
.\mvnw.cmd spring-boot:run
```

应用连接数据库时，Flyway 会自动执行 `src/main/resources/db/migration` 中尚未执行的迁移。首次初始化完成后，可以检查数据表和迁移记录：

```powershell
docker compose exec postgres psql -U crm_user -d advertiser_crm -c "\dt"
docker compose exec postgres psql -U crm_user -d advertiser_crm -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

如只需要查看简洁测试结果，可以运行：

```powershell
.\scripts\test.cmd
```

每项测试完成后会立即分行输出结果；测试失败时会输出完整 Maven 和 Spring 日志，便于排查问题。直接运行 `.\mvnw.cmd test` 时仍会显示完整构建过程。

验证地址：

- 健康检查：http://localhost:8080/actuator/health
- Swagger UI：http://localhost:8080/swagger-ui.html
- OpenAPI JSON：http://localhost:8080/v3/api-docs

## 用户认证与权限

当前用户接口：

- `POST /api/v1/auth/register`：公开提交内部员工注册申请，创建 `PENDING OPERATOR`。
- `POST /api/v1/auth/login`：登录并取得 Bearer JWT。
- `POST /api/v1/users`：管理员创建用户。
- `GET /api/v1/users?page=1&size=20`：管理员分页查询用户列表。
- `GET /api/v1/users/{id}`：管理员查询用户详情。
- `PATCH /api/v1/users/{id}`：管理员修改用户、角色或状态。
- `DELETE /api/v1/users/{id}`：管理员物理删除用户。

公开注册不会创建管理员，注册账号必须由管理员激活后才能登录。仅在本地开发数据库首次初始化管理员时，先注册一个普通账号，再通过 pgAdmin Query Tool 或 `psql` 执行：

```sql
UPDATE users
SET role = 'ADMIN', status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
WHERE LOWER(username) = LOWER('替换为你的用户名');
```

重新登录后，把返回的 `accessToken` 填入 Swagger 的 **Authorize** 对话框。用户管理接口只允许 `ADMIN`；管理员通过 `PATCH /api/v1/users/{id}` 将注册账号从 `PENDING` 改为 `ACTIVE` 后，员工才能登录。合法的 `OPERATOR` Token 访问用户管理接口会返回 403；`PENDING` 或 `DISABLED` 账号不能登录，已有 Token 也不能继续访问受保护接口。系统至少保留一个 `ACTIVE ADMIN`；降级、禁用或删除最后一个可用管理员时返回 `409 USER_LAST_ACTIVE_ADMIN_REQUIRED`。

公开认证接口使用单实例内存限流：同一“客户端 IP + 用户名”在 5 分钟内最多发起 5 次登录，同一客户端 IP 在 60 分钟内最多提交 3 次注册。超限返回 `429 Too Many Requests`、`AUTH_RATE_LIMITED` 和 `Retry-After`；成功登录会清除对应登录计数。阈值可通过 `AUTH_LOGIN_MAX_ATTEMPTS`、`AUTH_LOGIN_WINDOW_MINUTES`、`AUTH_REGISTRATION_MAX_ATTEMPTS` 和 `AUTH_REGISTRATION_WINDOW_MINUTES` 调整。当前直接使用连接地址识别客户端；部署到可信反向代理后，应先由基础设施正确处理转发地址再启用真实客户端 IP。

## 广告主管理

广告主接口：

- `POST /api/v1/advertisers`：创建广告主。
- `GET /api/v1/advertisers?page=1&size=20`：分页查询广告主列表。
- `GET /api/v1/advertisers/{id}`：查询广告主详情。
- `PATCH /api/v1/advertisers/{id}`：局部修改广告主档案。
- `PATCH /api/v1/advertisers/{id}/status`：启用或禁用广告主。
- `DELETE /api/v1/advertisers/{id}`：物理删除广告主。

用户、广告主和广告主分类三个列表接口均默认返回第 1 页、每页 20 条，`size` 最大为 100。分页数据位于统一响应的 `data.items`，并同时返回 `page`、`size`、`total` 和 `totalPages`。

广告主分类接口：

- `POST /api/v1/advertiser-categories`：创建分类。
- `GET /api/v1/advertiser-categories?page=1&size=20`：分页查询分类列表。
- `GET /api/v1/advertiser-categories/{id}`：查询分类详情。
- `PATCH /api/v1/advertiser-categories/{id}`：修改分类、状态或展示顺序。
- `DELETE /api/v1/advertiser-categories/{id}`：删除分类；已有广告主保留，`categoryId` 自动置空。

`ADMIN` 可以执行上述全部操作；`OPERATOR` 只能查询广告主和分类。新建或修改广告主时，只能分配状态为 `ACTIVE` 的分类和负责人。广告主名称、分类名称不区分大小写唯一，非空注册编号全局唯一。

局部修改时使用 `clearCategory: true` 或 `clearOwner: true` 可以主动解除广告主已有的分类或负责人；清除标记不能和对应的新 ID 同时提供。

停止数据库和管理页面：

```powershell
docker compose down
```

如需连同应用一起使用容器启动：

```powershell
docker compose --profile full up --build
```

## API 公共约定

业务接口显式返回统一的 `ApiResponse<T>`，包含 `success`、`code`、`message`、`data`、`timestamp` 和 `requestId`。公共错误码统一使用 `COMMON_*` 前缀，后续业务模块使用各自模块前缀。

每个请求都会返回 `X-Request-ID` 响应头。客户端可以传入由字母、数字、点、下划线和短横线组成且不超过 64 个字符的 Request ID；非法或缺失的值会被服务端替换。相同 ID 会写入响应体和请求完成日志。

全局异常处理统一转换参数校验、非法 JSON、资源不存在、数据冲突和未知异常。客户端响应不得包含堆栈、SQL、文件路径、密码或 Token；未知异常的完整信息只记录在后端日志中。

## 模块边界

```text
com.internship.crm
├── common       # 跨模块技术能力
├── config       # Spring 基础配置
├── auth         # 认证与权限
├── user         # 用户管理
└── advertiser   # 广告主管理与分类
```

业务代码采用“按业务模块分包、模块内部传统分层”的统一规范。开发者先按业务定位模块，再按职责定位代码：

```text
user/
├── controller/  # REST 接口入口，只处理 HTTP 协议和参数校验
├── service/     # 用户业务规则与事务边界
├── mapper/      # MyBatis-Plus 数据访问接口
├── entity/      # 数据库实体与枚举
├── dto/
│   ├── request/ # Controller 接收的请求对象
│   └── response/# Controller 返回的响应对象
└── exception/   # 用户模块业务错误码
```

`advertiser` 模块遵循相同结构。`auth` 模块保留认证专用的 `security` 和 `token` 子包，同时使用 `controller`、`service`、`dto/request`、`dto/response` 和 `exception`。公共技术能力按职责划分为：

```text
common/
├── response/    # 统一 API 响应
├── exception/   # 公共错误码、业务异常和全局异常处理
└── filter/      # 请求编号与日志过滤器
```

新业务模块进入开发阶段时再创建这些分层，避免提前建立空壳目录。禁止重新引入含义重叠的 `web`、`api`、`domain` 或 `error` 包，以保证所有模块命名一致。

## 设计文档

- [Sprint 1 开发流程](docs/spring1.md)
- [Sprint 1 数据库设计](docs/database-design.md)
