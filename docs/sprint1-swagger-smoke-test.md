# Sprint 1 Swagger 冒烟测试计划与验收记录

## 1. 目的

本流程用于 Sprint 1 功能合并完成后的本地验收。测试人员通过 Swagger UI 模拟真实客户端调用，快速确认以下能力可以协同工作：

- Spring Boot 能正常启动。
- PostgreSQL 可以连接，Flyway 迁移状态正常。
- Swagger/OpenAPI 文档可以访问。
- 注册、管理员审批、登录、JWT 鉴权和 RBAC 权限有效。
- 用户管理 CRUD 可用。
- 广告主 CRUD、状态管理和分类管理可用。
- 统一响应、异常处理和请求日志正常。

该流程属于“广覆盖、浅验证”的冒烟测试，不替代项目现有的单元测试、MockMvc 测试和数据库集成测试。

## 2. 测试范围与规则

### 2.1 本次范围

| 模块 | 验证内容 |
| --- | --- |
| 基础环境 | 应用启动、数据库连接、Flyway、健康检查、Swagger UI |
| 认证 | 注册、管理员审批、登录、JWT Authorize、无效或缺失 Token |
| 用户 | 创建、列表、详情、局部修改、删除 |
| 权限 | ADMIN 写权限、OPERATOR 只读权限、401 和 403 |
| 广告主分类 | 创建、列表、详情、修改、删除 |
| 广告主 | 创建、列表、详情、修改、状态切换、解除关联、删除 |
| 公共能力 | 统一响应、业务错误、参数校验、requestId、请求日志 |

### 2.2 测试数据规则

- 仅在本地开发数据库执行，不连接生产或共享数据库。
- 测试用户名、分类名和广告主名称统一以 `smoke_` 开头。
- 每轮增加时间后缀，例如 `20260822_1400`，避免唯一键冲突。
- 使用临时测试密码，不得使用个人真实密码，也不得把 Token、`.env` 或真实密码写入本文档。
- 记录创建接口返回的 `operatorUserId`、`ownerUserId`、`categoryId` 和 `advertiserId`，后续步骤使用真实返回值替换占位符。
- 测试完成后删除本轮创建的数据。

## 3. 测试前准备

### 3.1 确认代码分支

```powershell
git switch sprint1-backend-development
git pull --ff-only origin sprint1-backend-development
git status -sb
```

预期：工作区干净，本地分支与 `origin/sprint1-backend-development` 一致。

### 3.2 检查本地配置

1. 项目根目录存在未被 Git 跟踪的 `.env`。
2. `POSTGRES_PASSWORD`、`PGADMIN_DEFAULT_PASSWORD` 不再是 `change_me`。
3. `JWT_SECRET` 已填写本机随机生成的 Base64 密钥。
4. 不要在截图、测试记录或终端分享中展示这些值。

### 3.3 启动 PostgreSQL 和 pgAdmin

```powershell
docker compose up -d postgres pgadmin
docker compose ps
```

预期：`postgres` 显示为 `healthy`，pgAdmin 可以通过 http://localhost:5150 访问。

### 3.4 运行自动化测试

```powershell
.\scripts\test.cmd
```

预期：85 项测试全部通过。如果自动化测试失败，应先修复失败，不继续冒烟测试。

### 3.5 启动应用

```powershell
.\mvnw.cmd spring-boot:run
```

保持该终端运行，并检查启动日志中没有数据库连接或 Flyway 迁移错误。

### 3.6 检查入口

| 检查项 | 地址 | 预期 |
| --- | --- | --- |
| 健康检查 | http://localhost:8080/actuator/health | HTTP 200，状态为 `UP` |
| Swagger UI | http://localhost:8080/swagger-ui.html | 页面正常加载，接口按模块显示 |
| OpenAPI JSON | http://localhost:8080/v3/api-docs | HTTP 200，返回 OpenAPI JSON |

## 4. 准备测试账号

### 4.1 准备本地 ADMIN

如果本地已经存在可用的开发管理员，直接登录即可。首次测试且没有管理员时：

1. 在 Swagger 中执行 `POST /api/v1/auth/register`，注册一个本地开发账号。
2. 记录注册时使用的用户名，不在文档中记录密码。
3. 在 pgAdmin Query Tool 中执行以下 SQL，将该账号提升为管理员：

```sql
UPDATE users
SET role = 'ADMIN', status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
WHERE LOWER(username) = LOWER('替换为本地开发管理员用户名');
```

4. 重新登录，使新签发的 Token 包含最新角色。

公开注册只能创建 `PENDING OPERATOR`，不能通过注册请求直接创建管理员或启用账号。

### 4.2 注册本轮 OPERATOR

在 Swagger 中执行 `POST /api/v1/auth/register`：

```json
{
  "username": "smoke_operator_<时间后缀>",
  "password": "<本轮临时测试密码>",
  "displayName": "冒烟测试操作员",
  "email": "smoke_operator_<时间后缀>@example.com"
}
```

预期：

- HTTP 201。
- `success` 为 `true`，`code` 为 `OK`。
- `data.role` 为 `OPERATOR`，`data.status` 为 `PENDING`。
- 记录 `data.id` 为 `operatorUserId`。

### 4.3 管理员审批注册账号

先登录 ADMIN 并在 Swagger 中填入 ADMIN Token，再执行 `PATCH /api/v1/users/{operatorUserId}`：

```json
{
  "status": "ACTIVE"
}
```

预期：HTTP 200，响应中的 `data.status` 为 `ACTIVE`。

### 4.4 分别登录两个角色

执行 `POST /api/v1/auth/login`，分别取得 ADMIN 和 OPERATOR 的 `data.accessToken`。

```json
{
  "username": "<用户名>",
  "password": "<对应密码>"
}
```

预期：HTTP 200，响应中包含：

- `data.accessToken`
- `data.tokenType` 为 `Bearer`
- `data.expiresIn` 大于 0
- 正确的用户、角色和状态

测试过程中只把 Token 临时保存在本机剪贴板，不写入 Git 或测试文档。

## 5. Swagger Authorize 使用方法

1. 点击 Swagger 页面右上角的 **Authorize**。
2. 在 `bearerAuth` 输入框粘贴 `accessToken` 的值。
3. 不需要手动添加 `Bearer ` 前缀。
4. 点击 **Authorize**，然后关闭弹窗。
5. 切换角色时先点击 **Logout**，再填入另一个 Token。

## 6. 按顺序执行冒烟测试

### 6.1 未登录访问

先在 Authorize 中清除 Token，再执行 `GET /api/v1/advertisers`。

预期：

- HTTP 401。
- `success` 为 `false`。
- 返回统一错误结构，包含 `code`、`message`、`timestamp` 和 `requestId`。

### 6.2 OPERATOR 权限

使用 OPERATOR Token Authorize：

1. `GET /api/v1/advertisers`：预期 HTTP 200。
2. `GET /api/v1/advertiser-categories`：预期 HTTP 200。
3. `POST /api/v1/advertisers`：预期 HTTP 403。
4. `GET /api/v1/users`：预期 HTTP 403。

这组结果证明 OPERATOR 能读取广告主数据，但不能维护广告主或访问用户管理。

### 6.3 ADMIN 创建负责人用户

切换为 ADMIN Token，执行 `POST /api/v1/users`：

```json
{
  "username": "smoke_owner_<时间后缀>",
  "password": "<本轮临时测试密码>",
  "displayName": "冒烟测试负责人",
  "email": "smoke_owner_<时间后缀>@example.com",
  "role": "OPERATOR",
  "status": "ACTIVE"
}
```

预期：HTTP 201，记录响应中的 `data.id` 为 `ownerUserId`。

继续执行：

1. `GET /api/v1/users`：预期 HTTP 200，列表中存在该用户。
2. `GET /api/v1/users/{ownerUserId}`：预期 HTTP 200，字段与创建结果一致。
3. `PATCH /api/v1/users/{ownerUserId}`：修改显示名称。

```json
{
  "displayName": "冒烟测试负责人-已修改"
}
```

预期：HTTP 200，`data.displayName` 已更新，用户仍为 `ACTIVE`。

### 6.4 ADMIN 创建和修改广告主分类

执行 `POST /api/v1/advertiser-categories`：

```json
{
  "name": "smoke_category_<时间后缀>",
  "description": "Sprint 1 Swagger 冒烟测试分类",
  "status": "ACTIVE",
  "sortOrder": 10
}
```

预期：HTTP 201，记录 `data.id` 为 `categoryId`。

继续执行：

1. `GET /api/v1/advertiser-categories`：预期 HTTP 200，列表中存在该分类。
2. `GET /api/v1/advertiser-categories/{categoryId}`：预期 HTTP 200。
3. `PATCH /api/v1/advertiser-categories/{categoryId}`：

```json
{
  "description": "Sprint 1 Swagger 冒烟测试分类-已修改",
  "sortOrder": 20
}
```

预期：HTTP 200，说明和展示顺序已更新，状态仍为 `ACTIVE`。

### 6.5 ADMIN 创建广告主

执行 `POST /api/v1/advertisers`，把占位符替换为前面记录的真实 ID：

```json
{
  "name": "smoke_advertiser_<时间后缀>",
  "registrationNo": "SMOKE-<时间后缀>",
  "categoryId": <categoryId>,
  "ownerUserId": <ownerUserId>,
  "status": "ACTIVE",
  "website": "https://example.com",
  "address": "Local smoke test only",
  "description": "Sprint 1 Swagger 冒烟测试广告主"
}
```

预期：

- HTTP 201。
- `categoryId` 和 `ownerUserId` 与请求一致。
- `status` 为 `ACTIVE`。
- 记录 `data.id` 为 `advertiserId`。

### 6.6 查询和修改广告主

1. `GET /api/v1/advertisers`：预期 HTTP 200，列表中存在测试广告主。
2. `GET /api/v1/advertisers/{advertiserId}`：预期 HTTP 200，关联 ID 正确。
3. `PATCH /api/v1/advertisers/{advertiserId}`：

```json
{
  "website": "https://smoke.example.com",
  "address": "Local smoke test updated"
}
```

预期：HTTP 200，修改字段已经更新，未提供的字段保持不变。

### 6.7 切换广告主状态

执行 `PATCH /api/v1/advertisers/{advertiserId}/status`：

```json
{
  "status": "DISABLED"
}
```

预期：HTTP 200，`data.status` 为 `DISABLED`，分类和负责人关系保持不变。

再次调用该接口，把状态恢复为 `ACTIVE`，以便继续测试。

### 6.8 主动解除并恢复关联

执行 `PATCH /api/v1/advertisers/{advertiserId}`：

```json
{
  "clearCategory": true,
  "clearOwner": true
}
```

预期：HTTP 200，响应中的 `categoryId` 和 `ownerUserId` 为 `null` 或因统一 JSON 配置而不显示。

然后重新绑定：

```json
{
  "categoryId": <categoryId>,
  "ownerUserId": <ownerUserId>
}
```

预期：HTTP 200，两个关联 ID 恢复为请求值。

### 6.9 参数校验与统一异常

执行 `PATCH /api/v1/advertisers/{advertiserId}`：

```json
{
  "categoryId": <categoryId>,
  "clearCategory": true
}
```

预期：

- HTTP 400。
- `success` 为 `false`。
- 返回参数校验错误码和安全的错误详情。
- 不应出现 Java 堆栈或数据库内部信息。

随后重新查询广告主，确认原有关联没有被错误修改。

### 6.10 删除分类时解除数据库关联

确保测试广告主当前绑定测试分类，然后执行：

1. `DELETE /api/v1/advertiser-categories/{categoryId}`：预期 HTTP 200。
2. `GET /api/v1/advertisers/{advertiserId}`：预期 HTTP 200，`categoryId` 为 `null` 或不显示，广告主仍然存在。

该步骤验证分类删除后，数据库外键按设计执行 `ON DELETE SET NULL`。

## 7. 清理本轮测试数据

继续使用 ADMIN Token，按以下顺序清理：

1. `DELETE /api/v1/advertisers/{advertiserId}`。
2. `DELETE /api/v1/users/{ownerUserId}`。
3. `DELETE /api/v1/users/{operatorUserId}`。

测试分类已在 6.10 中删除。如果中途停止测试，应根据已记录的 ID 清理已经创建的数据。

每个删除接口预期 HTTP 200。再次查询对应 ID 时应返回 HTTP 404。

本地开发 ADMIN 可以保留，供下一轮测试使用；不要把该账号密码写入仓库。

## 8. 日志检查

回到运行 Spring Boot 的终端，确认：

- 每个请求都有方法、路径、HTTP 状态和耗时。
- 请求日志包含 `requestId`，并能与响应中的 `requestId` 对应。
- 401、403、400 等预期错误不会输出敏感信息。
- 整个流程中没有意外的 ERROR、数据库异常或 HTTP 500。

## 9. 测试记录模板

| 编号 | 场景 | 接口 | 预期状态 | 实际状态 | 结果 | 备注/证据 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 健康检查 | `/actuator/health` | 200 |  | 待测 |  |
| 2 | OPERATOR 注册、审批和登录 | `/api/v1/auth/*`、`PATCH /api/v1/users/{id}` | 201/200/200 |  | 待测 |  |
| 3 | 未登录访问受保护接口 | `GET /api/v1/advertisers` | 401 |  | 待测 |  |
| 4 | OPERATOR 查询广告主 | `GET /api/v1/advertisers` | 200 |  | 待测 |  |
| 5 | OPERATOR 执行写操作 | `POST /api/v1/advertisers` | 403 |  | 待测 |  |
| 6 | ADMIN 用户 CRUD | `/api/v1/users` | 200/201 |  | 待测 |  |
| 7 | ADMIN 分类 CRUD | `/api/v1/advertiser-categories` | 200/201 |  | 待测 |  |
| 8 | ADMIN 广告主 CRUD | `/api/v1/advertisers` | 200/201 |  | 待测 |  |
| 9 | 广告主状态切换 | `PATCH /api/v1/advertisers/{id}/status` | 200 |  | 待测 |  |
| 10 | 主动解除和恢复关联 | `PATCH /api/v1/advertisers/{id}` | 200 |  | 待测 |  |
| 11 | 冲突参数校验 | `PATCH /api/v1/advertisers/{id}` | 400 |  | 待测 |  |
| 12 | 分类删除后外键置空 | 分类 DELETE + 广告主 GET | 200 |  | 待测 |  |
| 13 | 数据清理和 404 确认 | DELETE + GET | 200/404 |  | 待测 |  |

### 9.1 用户与广告主 CRUD 补充验收记录（2026-08-22）

本节记录 Sprint 1 最终验收时实际执行的 CRUD 主链路。测试使用本地一次性 ADMIN 账号；截图仅保留响应区域，不包含密码、JWT 或其他密钥。

| 编号 | 模块 | 操作 | 接口 | 预期 | 实际 | 结果 | 证据 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CRUD-U1 | 用户 | 创建 | `POST /api/v1/users` | 201 | 201 | 通过 | `assets/sprint1-crud/user-create-201.png` |
| CRUD-U2 | 用户 | 查询详情 | `GET /api/v1/users/{id}` | 200 | 200 | 通过 | `assets/sprint1-crud/user-read-200.png` |
| CRUD-U3 | 用户 | 局部修改 | `PATCH /api/v1/users/{id}` | 200 | 200 | 通过 | `assets/sprint1-crud/user-update-200.png` |
| CRUD-U4 | 用户 | 删除 | `DELETE /api/v1/users/{id}` | 200 | 200 | 通过 | `assets/sprint1-crud/user-delete-200.png` |
| CRUD-U5 | 用户 | 删除后查询 | `GET /api/v1/users/{id}` | 404 / `USER_NOT_FOUND` | 404 / `USER_NOT_FOUND` | 通过 | `assets/sprint1-crud/user-not-found-404.png` |
| CRUD-A1 | 广告主 | 创建 | `POST /api/v1/advertisers` | 201 | 201 | 通过 | `assets/sprint1-crud/advertiser-create-201.png` |
| CRUD-A2 | 广告主 | 查询详情 | `GET /api/v1/advertisers/{id}` | 200 | 200 | 通过 | `assets/sprint1-crud/advertiser-read-200.png` |
| CRUD-A3 | 广告主 | 局部修改 | `PATCH /api/v1/advertisers/{id}` | 200 | 200 | 通过 | `assets/sprint1-crud/advertiser-update-200.png` |
| CRUD-A4 | 广告主 | 删除 | `DELETE /api/v1/advertisers/{id}` | 200 | 200 | 通过 | `assets/sprint1-crud/advertiser-delete-200.png` |
| CRUD-A5 | 广告主 | 删除后查询 | `GET /api/v1/advertisers/{id}` | 404 / `ADVERTISER_NOT_FOUND` | 404 / `ADVERTISER_NOT_FOUND` | 通过 | `assets/sprint1-crud/advertiser-not-found-404.png` |

补充验收结论：用户管理和广告主管理的创建、读取、更新、删除以及删除后错误响应均符合接口约定，CRUD 主链路通过。

## 10. 通过标准

以下条件全部满足，Sprint 1 Swagger 冒烟测试才算通过：

- 自动化测试 85/85 通过。
- 应用、数据库、健康检查和 Swagger UI 正常。
- 注册、管理员审批、登录和 JWT 鉴权正常。
- ADMIN 与 OPERATOR 权限边界符合设计。
- 用户、广告主和分类的核心 CRUD 流程正常。
- 广告主状态、负责人和分类关系可以正确修改。
- 统一成功响应和统一错误响应结构正常。
- 没有意外 HTTP 500、敏感信息泄露或数据库错误。
- 测试数据清理完成。

若任何必测项失败，应记录接口、请求体、响应、`requestId` 和相关日志，修复后重新执行失败步骤及其后续依赖步骤。

## 11. 手动测试与自动化测试的分工

当前 Sprint 首次验收建议手动执行 Swagger 冒烟测试，因为手动操作能同时发现以下问题：

- Swagger 文档是否容易理解。
- Authorize 和角色切换是否符合真实使用方式。
- 示例请求体是否可以直接使用。
- 接口顺序、响应内容和错误提示是否清晰。
- 合并后的完整应用是否真正可操作。

但手动测试不应替代自动化测试。稳定后的推荐流程是：

1. 每次提交由单元测试、MockMvc 和数据库集成测试快速回归。
2. 每个 Sprint 合并完成后，人工执行一次 Swagger 冒烟测试。
3. 当流程稳定且重复执行成本变高时，再把核心冒烟路径加入 Postman/Newman 或其他 API 自动化集合。
