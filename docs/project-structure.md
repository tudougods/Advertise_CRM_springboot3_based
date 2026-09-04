# Advertiser CRM 项目结构

## 1. 结构原则

后端以业务模块作为第一层目录，而不是把全项目的 Controller、Service、Mapper 分别堆在一起。开发者可以先根据业务找到模块，再在模块内沿调用层次定位代码。

```text
src/main/java/com/internship/crm/
├─ auth/          认证、JWT、限流和 Spring Security 接入
├─ user/          用户、角色和账号状态管理
├─ advertiser/    广告主、分类和负责人关系
├─ delivery/      广告类型和广告投放记录
├─ report/        投放统计、趋势和维度报表
├─ account/       广告主账户、消费和资金流水
├─ payment/       充值订单、模拟支付和回调审计
├─ common/        跨业务模块复用的响应、异常和请求上下文
└─ config/        应用级 Spring、MyBatis、OpenAPI 和时间配置
```

业务模块内部按实际需要使用以下分层：

| 目录 | 职责 |
| --- | --- |
| `controller` | HTTP 路由、权限声明、参数校验和统一响应封装 |
| `service` | 业务规则、事务边界和跨实体协作 |
| `mapper` | 数据库查询、写入和物理分页 |
| `entity` | 与数据库记录对应的持久化实体和状态枚举 |
| `dto/request` | 外部请求模型和输入约束 |
| `dto/response` | 对外响应模型，不暴露密码摘要等内部字段 |
| `exception` | 模块专属错误码 |
| `model` | 只在模块内部流转、且不直接对应数据库表的查询模型 |
| `validation` | 同一业务模块多个入口复用的领域输入规则 |

没有对应职责的模块不需要为了目录对称而创建空目录。例如 `report` 的聚合结果使用 `model`，不需要额外创建 `entity`。

## 2. 模块边界

- `common` 只放统一响应、分页、异常契约、Request ID 等不含具体业务语义的能力。
- `config` 只负责应用级 Bean 和框架配置，不承载业务判断。
- 业务错误码保留在所属模块，不能集中成一个难以维护的全局枚举。
- Controller 不直接访问 Mapper；数据库操作和事务由 Service 组织。
- Service 不拼接 HTTP 状态或响应结构；Controller 使用 `ApiResponse` 统一封装。
- 跨模块调用通过 Service 或明确的 Mapper 能力完成，不复制另一模块的业务规则。
- 仅被单一模块复用的规则留在该模块，例如支付引用格式位于 `payment/validation`，不放入 `common`。

## 3. 资源与辅助目录

```text
src/main/resources/
├─ application.yml       应用默认配置和环境变量映射
├─ db/migration/         按版本顺序执行的 Flyway 迁移
└─ mapper/               适合 XML 表达的 MyBatis 复杂查询

src/test/                与主代码模块结构对应的单元、Web 和 PostgreSQL 测试
docker/                  容器服务所需的静态配置
scripts/                 测试入口和可复现的 SQL 验证脚本
docs/                    迭代计划、设计、验收和性能证据
```

新增数据库结构必须使用新的 Flyway 版本，不能修改已经发布的历史迁移。简单 Mapper 查询可以使用注解或 MyBatis-Plus；复杂动态聚合查询放在 `resources/mapper`，并保持 Java Mapper 与 XML namespace 一致。

## 4. 文件清理规则

以下内容属于本地状态或生成物，不应提交：

- `.env`：包含本地运行配置；仓库只保留 `.env.example`。
- `target/`、`.maven/`：构建输出和本地 Maven 缓存。
- `.vscode/`、`.idea/`：个人 IDE 配置。
- `~$*.docx`：Word 打开文档时生成的临时锁文件。

`.gitignore` 和 `.dockerignore` 已覆盖以上内容。正式的设计文档、验收截图和演示文档可以保留在 `docs/`，但临时锁文件和构建产物必须清除。

## 5. 新增代码放置检查

提交新功能前确认：

1. 代码位于对应业务模块，而不是因为“可能复用”提前放进 `common`。
2. HTTP、业务和数据访问职责分别位于 Controller、Service、Mapper。
3. 对外请求和响应使用 DTO，数据库实体不直接作为接口响应。
4. 共享规则只有一个来源，Controller 校验与 Service 防御性校验保持一致。
5. 新目录中不存在空目录、临时文件、真实密钥或生成物。
6. 变更后运行 `scripts/test.cmd`，确认编译、迁移和完整回归通过。
