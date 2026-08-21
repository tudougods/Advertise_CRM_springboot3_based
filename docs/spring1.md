# Advertiser CRM Sprint 1 开发流程

> 说明：本文中的 **Sprint 1** 表示第一个开发迭代；**Spring Boot** 是项目使用的后端框架。

## 1. Sprint 目标

本迭代先完成一条可运行、可验证的后端主链路：

1. PostgreSQL 数据库能够通过 Flyway 自动初始化。
2. 用户可以注册和登录，密码经过 BCrypt 加密，接口使用 JWT 鉴权。
3. 管理员可以管理用户、角色和账号状态。
4. 已授权用户可以管理广告主分类和广告主档案。
5. 项目具备统一响应、异常处理、日志、Swagger 文档和基础自动化测试。

本迭代不开发 CSV 导入、广告账户、广告计划、统计报表和 Agent 功能。

## 2. 分支策略

| 分支 | 用途 | 合并目标 |
| --- | --- | --- |
| `main` | 稳定版本，只接收完整 Sprint | - |
| `sprint1-backend-development` | Sprint 1 集成与联调 | Sprint 完成后合并到 `main` |
| `feature/*` | 单个功能开发和测试 | `sprint1-backend-development` |
| `fix/*` | Sprint 1 联调期间的问题修复 | `sprint1-backend-development` |

每个新任务都从最新的 Sprint 1 集成分支创建：

```powershell
git switch sprint1-backend-development
git pull --ff-only origin sprint1-backend-development
git switch -c feature/<task-name>
```

功能测试通过后再提交和推送，并将 Pull Request 的目标分支设置为 `sprint1-backend-development`。Sprint 1 验收完成前，不把单个功能分支直接合并到 `main`。

## 3. 当前进度

| 顺序 | 任务 | 分支 | 当前状态 |
| --- | --- | --- | --- |
| 1 | 数据库设计 | `sprint-1-database-design` | 已合并，旧分支已删除 |
| 2 | 数据库初始化 | `feature/database-initialization` | 已完成并合并，旧分支已删除 |
| 3 | 通用 Web 基础能力 | `feature/common-web-foundation` | 已实现并通过测试，等待 PR 合并 |
| 4 | 用户管理 | `feature/user-management` | 待开始 |
| 5 | JWT 认证与 RBAC | `feature/authentication-rbac` | 待开始 |
| 6 | 广告主分类管理 | `feature/advertiser-category-management` | 待开始 |
| 7 | 广告主管理 | `feature/advertiser-management` | 待开始 |
| 8 | Sprint 联调与验收 | `test/sprint1-integration` | 待开始 |

状态只在任务通过测试并合并到 `sprint1-backend-development` 后更新为“已完成”。

## 4. 任务执行顺序

### 4.1 数据库初始化

主要工作：

- 使用 Flyway 创建 `users`、`advertiser_categories` 和 `advertisers`。
- 创建主键、外键、唯一索引、检查约束和查询索引。
- 验证首次迁移成功，并验证再次启动不会重复执行迁移。
- 使用 pgAdmin 在浏览器中查看表结构、迁移记录和测试数据。

完成标准：

- PostgreSQL 中存在三张核心业务表。
- `flyway_schema_history` 中的 `V1` 状态为成功。
- `.\scripts\test.cmd` 或 `.\mvnw.cmd test` 通过。
- 可以通过 `http://localhost:5150` 登录 pgAdmin 并连接 `advertiser_crm`。

### 4.2 通用 Web 基础能力

主要工作：

- 定义统一 API 返回结构。
- 实现全局异常处理和明确的错误码。
- 统一参数校验错误、业务异常和未知异常的响应。
- 建立基础日志规范，避免记录密码、JWT 等敏感信息。

完成标准：

- 成功和失败响应格式一致。
- 参数错误能够返回可读信息和正确的 HTTP 状态码。
- 关键异常有日志，但响应中不暴露堆栈和数据库细节。
- 响应头、响应体和日志使用一致的 Request ID。
- 公共 Web 行为具有 MockMvc 自动化测试。

### 4.3 用户管理

主要工作：

- 建立用户实体、DTO、Mapper、Service 和 Controller。
- 实现用户信息查询、分页、局部修改和状态变更。
- 使用 `ACTIVE`、`DISABLED` 管理账号状态，不提供物理删除。
- 保证用户名和邮箱的不区分大小写唯一性。

完成标准：

- 用户管理接口通过 Swagger 或 Postman 验证。
- 重复用户名、重复邮箱和非法状态能够被正确拒绝。
- 至少覆盖 Service 层核心规则的单元测试。

### 4.4 JWT 认证与 RBAC

主要工作：

- 实现注册和登录接口。
- 使用 BCrypt 保存密码摘要，不保存或记录明文密码。
- 签发并校验 JWT，处理无令牌、无效令牌和过期令牌。
- 实现 `ADMIN`、`OPERATOR` 两种角色的接口权限控制。
- 禁止普通注册直接创建 `ADMIN`。

完成标准：

- 正确账号可以登录并访问已授权接口。
- 错误密码、禁用账号和无效 JWT 均被拒绝。
- 普通用户无法访问管理员接口。

### 4.5 广告主分类管理

主要工作：

- 实现广告主分类的新增、查询、修改和启用/禁用。
- 分类名称不区分大小写唯一。
- 列表按 `sort_order` 排序。
- 禁用分类保留历史关系，但不能分配给新的广告主。

完成标准：

- 分类管理接口通过 Swagger 或 Postman 验证。
- 重复名称、空白名称和负数排序值能够被正确拒绝。
- 分类状态相关业务规则具有单元测试。

### 4.6 广告主管理

主要工作：

- 实现广告主档案的新增、分页查询、详情查询、修改和状态变更。
- 支持关联广告主分类和负责人。
- 只允许分配状态为 `ACTIVE` 的分类和负责人。
- 企业名称不区分大小写唯一，注册编号唯一。

完成标准：

- 广告主管理接口通过 Swagger 或 Postman 验证。
- 非法分类、非法负责人和重复企业信息能够被正确拒绝。
- 关键 Service 规则和 Mapper 查询具有测试。

### 4.7 Sprint 联调与验收

主要工作：

- 串联注册、登录、用户管理、分类管理和广告主管理流程。
- 补齐 Swagger 接口说明、请求示例和错误响应。
- 整理 Postman 集合或等价的接口测试记录。
- 在干净数据库上重新执行 Flyway 和全部自动化测试。
- 修复联调问题，不在此阶段临时增加新功能。

完成标准：

- `mvnw clean test` 全部通过。
- 应用可以通过 Docker Compose 配合本地 Maven 正常启动。
- 核心接口的成功、鉴权失败、权限不足和参数错误场景均已验证。
- Sprint 产出满足导师要求后，才创建从 `sprint1-backend-development` 到 `main` 的 Pull Request。

## 5. 第一周建议节奏

| 时间 | 开发重点 | 当天应形成的结果 |
| --- | --- | --- |
| 第 1 天 | 数据库初始化 | Flyway `V1`、三张核心表、初始化验证记录 |
| 第 2 天 | 通用 Web 基础能力、用户管理基础 | 统一响应、异常处理、用户 CRUD 主链路 |
| 第 3 天 | JWT 认证与 RBAC | 注册、登录、BCrypt、JWT、角色权限测试 |
| 第 4 天 | 广告主分类与广告主管理 | 两个管理模块的 CRUD、状态和关联规则 |
| 第 5 天 | 联调、Swagger、测试和修复 | 完整演示链路、测试结果和 Sprint 验收记录 |

这是一份建议节奏，不以“当天代码数量”为完成标准。如果前一项尚未测试通过，不应为了赶进度跳过验证或同时铺开多个未完成模块。

## 6. 单个任务的固定流程

每个功能都按照以下顺序进行：

1. 从最新的 `sprint1-backend-development` 创建功能分支。
2. 先确认需求、接口和数据规则，再开始编码。
3. 只修改当前任务所需文件，避免混入其他功能。
4. 完成编译、单元测试和本地接口测试。
5. 人工检查代码和测试结果。
6. 获得确认后再提交、推送并创建 Pull Request。
7. 合并回 `sprint1-backend-development` 后删除功能分支。
8. 更新本文的任务状态，再开始下一个任务。

## 7. Sprint 1 总体验收清单

- [ ] Flyway 可以在空数据库上完成初始化。
- [ ] 用户注册、登录和 JWT 鉴权可用。
- [ ] BCrypt 密码摘要和 RBAC 权限规则正确。
- [ ] 用户 CRUD 与启用/禁用可用。
- [ ] 广告主分类管理可用。
- [ ] 广告主 CRUD 与启用/禁用可用。
- [ ] 统一响应、异常处理和日志规范生效。
- [ ] Swagger 文档完整且与实际接口一致。
- [ ] 基础单元测试和接口测试通过。
- [ ] 不包含 CSV、报表或 Agent 等 Sprint 1 范围外功能。
- [ ] `sprint1-backend-development` 最终合并到 `main`。
