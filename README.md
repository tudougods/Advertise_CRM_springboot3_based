# Advertiser CRM Backend

广告商 CRM 后端原型的 Spring Boot 模块化单体骨架。当前阶段只包含工程基础设施，不包含业务表、实体、Mapper、Service、Controller 或 JWT 逻辑。

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

启动 PostgreSQL：

```powershell
docker compose up -d postgres
```

默认将容器的 PostgreSQL `5432` 端口映射到本机 `15432`，避免与本机已有 PostgreSQL 服务冲突。

启动应用：

```powershell
.\mvnw.cmd spring-boot:run
```

验证地址：

- 健康检查：http://localhost:8080/actuator/health
- Swagger UI：http://localhost:8080/swagger-ui.html
- OpenAPI JSON：http://localhost:8080/v3/api-docs

停止数据库：

```powershell
docker compose down
```

如需连同应用一起使用容器启动：

```powershell
docker compose --profile full up --build
```

## 模块边界

```text
com.internship.crm
├── common       # 跨模块技术能力
├── config       # Spring 基础配置
├── auth         # 认证与权限
├── user         # 用户管理
├── advertiser   # 广告商与联系人
├── advertising  # 广告账户与投放计划
├── metrics      # 日报导入
└── reporting    # 统计报表
```

各业务模块在进入对应开发阶段时再建立 Controller、Service、Domain 和 Mapper，避免提前创建空壳分层。

## 设计文档

- [Sprint 1 数据库设计](docs/database-design.md)
