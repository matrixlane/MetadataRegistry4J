## Why

系统要面向中国本土化场景优先交付两种形态：**个人版**（单机部署，零外部依赖）与**团队版**（局域网单服务器部署，多部门协作），同时为未来的云端多租户版保留兼容性。当前系统在这些目标上存在硬性阻塞：

1. **数据库与 SQL Server 深度耦合**：20 个 Flyway 迁移脚本为纯 T-SQL（UNIQUEIDENTIFIER、DATETIME2、sys 目录查询、游标）；30 个实体类硬编码 `@Table(schema = "mdr")`；初始化向导的数据清理逻辑（`clearBusinessDataOnly`）使用 `INFORMATION_SCHEMA`、`NOCHECK CONSTRAINT`、`DBCC CHECKIDENT` 等 SQL Server 专有语法。SQLite 完全无法启动，达梦等国产数据库亦无接入路径。
2. **初始化向导只认 SINGLE 模式**：`InitWizardService.setup` 显式拒绝其他模式，前端第二张模式卡片处于 TBD 禁用态，"个人版/团队版"的形态差异无处表达。
3. **数据库只能在部署前通过配置文件指定**：用户无法在初始化流程中选择/配置数据库，也无法在系统设置中变更；更没有把数据从 SQLite 迁移到 SQL Server/达梦等独立数据库的路径——个人版用户的成果被锁定在文件库中。

## What Changes

- **引入部署版本（edition）概念**：新增 `PERSONAL` / `TEAM` 两个版本（`CLOUD` 仅作枚举占位，本变更不实施），持久化于 `system_setting`，由初始化向导第一步选择并写入，替换现有"运行模式"单一 SINGLE 语义（SINGLE 作为 TEAM 的默认注册表策略保留）。
- **数据库可移植性基线，三种数据库同为第一类支持**：
  - SQLite（个人版默认）、SQL Server、**达梦 DM8**（与 SQL Server 同等地位，团队版可选）均为第一类受支持数据库。
  - 移除全部实体上的 `schema = "mdr"` 硬编码（30 个文件），schema/表空间归属改为可按方言配置。
  - 迁移脚本按方言分目录管理（`db/migration/sqlserver`、`db/migration/sqlite`、`db/migration/dm`），存量 T-SQL 脚本基线化；新增迁移必须提供可移植实现或三方言实现。
  - 初始化向导与恢复出厂的数据清理逻辑改为方言感知的可移植实现，废弃 `INFORMATION_SCHEMA`/`NOCHECK`/`DBCC` 直写。
- **初始化向导按版本差异化**：第一步选择版本；个人版精简步骤（无需注册机构向导的治理字段）；团队版保留完整治理配置并支持后续添加多个注册机构；云端多租户仅保留版本枚举与数据模型兼容，不实施租户隔离。
- **数据库配置能力（新增）**：初始化向导中增加数据库选择/配置步骤（SQLite 文件路径，或 SQL Server/达梦的主机、端口、库名、凭据），支持连接测试；系统设置中同样提供数据库配置查看与变更入口。配置写入本地配置文件（`application-local.yml`），按引导重启生效。
- **数据库迁移能力（新增）**：支持将当前数据库中的全部数据迁移到另一种数据库（典型场景：SQLite → SQL Server/达梦）。提供迁移向导：源库全量导出 → 目标库连接测试与建库 → 导入 → 行数校验 → 切换配置。迁移期间系统进入只读维护状态；原库保持不动作为天然回滚保障。
- **微信/企业微信扫码登录：本变更不做**。未来方向记录在案：团队版规划企业微信扫码登录，云端多租户版规划微信扫码登录（均需企业资质与公网回调，届时单独立项）。

**BREAKING**：实体移除 `schema = "mdr"` 后，存量 SQL Server 部署需要通过基线迁移将表从 `mdr` schema 归并或保持映射兼容；升级路径在设计文档中定义。

## Capabilities

### New Capabilities

- `deployment-edition`：部署版本（PERSONAL/TEAM）的选择、持久化、查询与按版本的功能约束规则；CLOUD 占位。
- `database-portability`：数据库方言支持矩阵（SQLite、SQL Server、达梦 DM8）、可移植性设计规则（方言中立 SQL、schema 解耦、迁移三轨）。
- `system-initialization`：按版本差异化的初始化向导流程（版本选择 → 数据库配置 → 治理配置 → 管理员设置 → 提交/放弃），方言感知的数据清理语义。
- `datasource-configuration`：初始化向导与系统设置中的数据库选择、连接参数配置、连接测试、配置持久化与重启生效。
- `database-migration`：跨数据库的数据迁移（导出 → 建库 → 导入 → 校验 → 切换），只读维护状态与回滚语义。

### Modified Capabilities

- `factory-reset`：恢复出厂的数据清理逻辑必须从 SQL Server 专有语法改为方言可移植实现，行为语义不变。

## Impact

- **实体层**：`metadata-registry-common-facilities`、`metadata-registry-data-specification` 等模块共 30 个实体类的 `@Table` 注解。
- **迁移层**：`metadata-registry-web/src/main/resources/db/migration/` 全部 20 个存量脚本的目录重组与基线化；新增 sqlite、dm 两套脚本。
- **初始化与运维**：`InitWizardService`、`InitWizardController`、`FactoryResetService`、`SystemSettingService`、`DataInitializer`；新增数据源配置与迁移相关服务/控制器。
- **配置**：`application.yml`（数据源/方言按 profile 拆分：`personal`/`team`）；`application-local.yml` 作为用户可写配置层。
- **前端**：`InitWizard.vue`（版本选择 + 数据库配置步骤）、系统设置页（数据源配置与迁移向导入口）。
- **依赖**：新增 `hibernate-community-dialects`（SQLiteDialect）、SQLite JDBC 驱动、达梦 JDBC 驱动（含官方 Hibernate 方言）。
- **不涉及**：账号/认证体系（`Account`、`AuthService`、`SecurityConfig`）不做变更；微信/企业微信登录不在本期范围。
- **不影响**：ISO 11179 领域模型语义、既有业务功能与 REST API 契约（除 init-wizard 接口扩展与新增数据源/迁移接口）。
