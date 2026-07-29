## Context

系统计划面向中国本土化场景交付两种形态：**个人版**（单机、零外部依赖、SQLite）与**团队版**（局域网单服务器、多部门协作、SQLite / SQL Server / 达梦 DM8），云端多租户版只做兼容性预留。数据库选型上，**达梦与 SQL Server 同等重要**（医疗信息化客户普遍有国产化要求）；用户还需要在初始化与系统设置中配置数据库，并把数据从 SQLite 迁移到独立数据库。

现状（已核实）：

- 20 个 Flyway 迁移脚本为纯 T-SQL；30 个实体类硬编码 `@Table(schema = "mdr")`；数据清理逻辑直写 `INFORMATION_SCHEMA`/`NOCHECK`/`DBCC`。
- `InitWizardService.setup()` 显式拒绝非 SINGLE 模式；前端第二张模式卡片为 TBD 禁用态。
- `application.yml` 硬编码 `SQLServerDialect` 与 `default_schema: mdr`；`spring.config.import: optional:application-local.yml` 已存在（是用户可写配置层的天然落点）。
- 主键全部为 UUID（`GenerationType.UUID`），无自增 ID 跨库冲突负担；个别配置表使用 `BIGINT IDENTITY`。

## Goals / Non-Goals

**Goals:**

- SQLite、SQL Server、达梦 DM8 三种数据库同为第一类支持：应用可完整启动、初始化、运行全部核心功能、恢复出厂。
- 初始化向导支持版本选择（PERSONAL/TEAM）与数据库配置（选择方言、连接参数、连接测试）。
- 系统设置中可查看/变更数据库配置（重启生效）。
- 提供跨库数据迁移能力（典型：SQLite → SQL Server/达梦），含校验与回滚保障。
- 初始化与恢复出厂的 setup/commit/abort 语义保持幂等可恢复，且在三种方言上行为一致。

**Non-Goals:**

- 不实施云端多租户（不建租户隔离、不加 `tenant_id` 列、不做配额）；仅枚举与文档预留。
- 不做微信扫码/企业微信扫码/任何第三方登录（未来方向：团队版→企业微信，多租户版→微信扫码，另行立项）。
- 不实现数据源热切换（配置变更统一走"写配置 + 引导重启"，不做运行时无损切换）。
- 不重构 ISO 11179 领域模型与既有业务功能。

## Decisions

### D1：用"部署版本（edition）"而非扩展 running_mode 表达产品形态

`system_setting` 新增 `system.edition`，取值 `PERSONAL` / `TEAM`（`CLOUD` 仅枚举占位）。`system.running_mode` 保留为**注册表治理策略**（SINGLE/MULTI），与版本正交。PERSONAL 强制 SINGLE 且不可修改；TEAM 默认 SINGLE，管理员可在系统设置中切换 MULTI。

### D2：数据库方言矩阵（三方同为第一类）

| 方言 | 方言类 | 驱动 | 典型用途 |
|---|---|---|---|
| SQLite | `SQLiteDialect`（hibernate-community-dialects） | `sqlite-jdbc` | 个人版默认；小团队可选 |
| SQL Server | `SQLServerDialect` | `mssql-jdbc`（现有） | 团队版默认 |
| 达梦 DM8 | 达梦官方 Hibernate 方言（DM8 驱动附带；备选 Oracle 兼容方言，实施时实测确定） | 达梦 JDBC 驱动 | 团队版（国产化要求客户） |

方言选择由数据源配置驱动（见 D10），不再与 profile 硬绑定：profile 仅提供默认值（`personal`→SQLite，`team`→SQL Server）。SQLite 下连接池强制 `maximum-pool-size: 1` 并开启 WAL；达梦下 schema 取专用用户（建议建 `MDR` 用户/模式）。

备选方案：运行时自动探测 JDBC URL 推断方言。否决理由：隐式行为难排错；但数据源配置层需要把"方言"显式持久化，供迁移与清理组件使用（见 D5、D10）。

### D3：实体 schema 解耦，归属交给部署配置

移除全部 30 个实体类的 `@Table(schema = "mdr")`。schema 归属通过 `hibernate.default_schema` 按数据源配置指定：SQL Server 继续用 `mdr`（与存量库兼容）；SQLite 不设置；达梦使用其用户默认模式（如 `MDR`）。实体层方言中立。

### D4：迁移脚本按方言分目录 + 三轨规则

- 目录：`db/migration/sqlserver/`、`db/migration/sqlite/`、`db/migration/dm/`，`spring.flyway.locations` 按当前数据源方言指向对应目录。
- 存量 20 个 T-SQL 脚本保留为 SQL Server 基线；为 SQLite 与达梦分别生成等价基线脚本（SQLite：UUID→TEXT、去 sys 目录查询/游标；达梦：UUID→VARCHAR(36)、DATETIME2→TIMESTAMP、sys 查询→达梦数据字典、T-SQL 游标→达梦 PL/SQL 或应用侧逻辑）。
- 新规则：新增迁移 MUST 同时提供三方言脚本，或证明为可移植 SQL；Hibernate DDL 可表达的结构变更优先交由 `ddl-auto` 管理，迁移脚本仅承载索引、约束与数据迁移。

### D5：数据清理改为 JPA 元模型驱动 + 方言 FK 控制

废弃 `clearBusinessDataOnly()` 与 `FactoryResetService` 中的 T-SQL 直写，引入 `DatabaseCleaner` 组件：

- 表发现：从 JPA `Metamodel` 枚举实体表（排除 `account`、`system_setting`、`flyway_schema_history` 等保留表），替代 `INFORMATION_SCHEMA`/`sys.*` 查询。
- FK 处理封装为方言策略：SQLite 用 `PRAGMA foreign_keys`；SQL Server 用约束控制语句；达梦用 `ALTER TABLE ... DISABLE CONSTRAINT`（Oracle 风格）。
- 不执行 `DBCC CHECKIDENT`（主键全为 UUID；个别 IDENTITY 配置表允许降级为普通 DELETE）。
- 按依赖序重试删除（最多 N 轮）作为 FK 控制失败时的方言无关兜底。

### D6：native 查询收敛到方言感知仓储

`ItemMappingRepository.findReachableItemIdsByRecursiveCte` 移入按方言拆分的仓储 fragment（SQL Server / SQLite / 达梦各自实现；三者均支持递归 CTE 但类型与语法细节不同）。规则：业务代码中的 native SQL MUST 位于方言命名的实现类中，接口保持方言中立。

### D7：初始化向导按版本差异化

- 第 1 步选择版本（默认 PERSONAL）；第 2 步数据库配置（见 D10）；后续步骤按版本裁剪：PERSONAL 自动创建"个人注册机构"并跳过治理字段；TEAM 保留完整治理配置。
- 版本在 `commit` 时写入 `system.edition`，写入后不可变更（变更需恢复出厂）。
- `InitWizardSetupRequest` 增加 `edition` 字段；`InitWizardStatusResponse` 返回 `edition` 供前端路由与功能开关。

### D8：数据源配置（向导 + 系统设置）

- 配置模型：`vendor`（sqlite/sqlserver/dm）、`jdbcUrl` 或分解参数（主机/端口/库名/文件路径）、用户名/密码、`hibernate.default_schema`、连接池参数（按方言给默认值）。
- 持久化：写入项目根目录 `application-local.yml`（`spring.config.import` 已支持）。该文件归部署者所有，纳入备份建议；不进版本库。
- 生效方式：统一"写配置 + 引导重启"。不做运行时热切换。
- 连接测试：保存前后端用给定参数建立测试连接（含方言识别与版本探测），失败则不入库、不落地。
- 初始化向导中的位置：数据库配置为第 2 步（版本选择之后）。提交后写入配置并引导重启；重启后向导状态从目标库重新评估（`wizard-state` 自然回到 READY），用户继续后续步骤。系统设置中的变更走同一管线，额外要求管理员确认"变更将导致重启"。
- 密码保护：配置中的口令不明文回显；界面只读展示时打码。

### D9：数据库迁移（跨库数据搬家）

- 迁移管线（应用层、方言中立）：**源库全量导出 → 目标库连接测试/建库 → 导入 → 逐表行数校验 → 切换 `application-local.yml` → 引导重启**。
- 导出格式：按 FK 依赖序逐表导出为 JSON Lines（每行一条记录，含列名），主键原样保留（UUID 无冲突）；`BIGINT IDENTITY` 表（如 `version_history`）导出显式 ID 并在导入时保留。
- 导入：目标库先由 Hibernate/Flyway 完成建表与基线迁移，再按序批量写入；导入期间目标库为空库是前置条件（非空则拒绝，防止合库事故）。
- 安全语义：迁移期间系统进入**只读维护状态**（拒绝写操作）；原库全程不修改，切换失败时回滚 = 恢复配置文件并重启。
- 与联邦能力的关系：这是整库搬迁（运维动作），不同于 `registry-federation-merge-request` 的增量协作合并；两者共享"导出/导入 + 校验"的工具代码但不共享语义。

### D10：达梦接入的技术要点

- 方言：优先达梦官方 Hibernate 方言（DM8 驱动附带），验证不过时退回 Oracle 兼容方言并记录差异。
- 类型映射：UUID→`VARCHAR(36)`；`TEXT/NVARCHAR(MAX)`→`CLOB`；`DATETIME2`→`TIMESTAMP`；`BIT`→`BOOLEAN`。
- 序列/自增：达梦支持 IDENTITY，与 SQL Server 语义接近；`identifier_strategy_config` 等表直接映射。
- 验证环境：本地 DM8 实例（评估版）；CI 接入列为可选项（Docker 镜像来源不稳定）。

## Risks / Trade-offs

- [SQLite 单写者并发上限低] → 团队版默认 SQL Server/达梦；SQLite 建议 ≤10 人；连接池强制为 1。
- [达梦官方方言与标准 Hibernate 行为存在边角差异] → 以双方言（官方/Oracle 兼容）实测结论为准并记录；三方 CI 验证尽早建立。
- [配置写文件 + 重启生效的体验断点] → 向导与系统设置中明确引导；重启后状态自动从目标库恢复评估，无需用户记忆。
- [迁移导出/导入在大数据量下耗时长] → 分批提交 + 进度反馈；维护状态避免迁移期间写入漂移。
- [存量 SQL Server 部署升级时迁移目录变更可能触发 Flyway checksum 冲突] → `baseline-on-migrate` 已覆盖；升级指南要求先备份并提供 `flyway repair` 说明。

## Migration Plan

1. 合并顺序：D3（schema 解耦）→ D4（迁移三轨）→ D5/D6（清理与 native 查询方言化）→ D2（SQLite 落地）→ D10（达梦落地）→ D7（向导版本化）→ D8（数据源配置）→ D9（数据库迁移）。
2. 存量 SQL Server 部署升级：备份 → 部署新版本 → 基线续跑 → 验证数据完整。
3. 回滚策略：无破坏性变更（仅新增表/列/设置键与新增配置项）；回滚应用版本即可。

## Open Questions

- 达梦官方方言 vs Oracle 兼容方言的实测结论（首个达梦接入任务产出）。
- 达梦 CI 验证环境（Docker 镜像可用性与许可）。
- 迁移导出是否需要压缩与加密（含敏感配置字段时）。
- 未来方向（不在本变更）：团队版企业微信扫码登录、云端多租户版微信扫码登录——均需企业资质与公网回调，另行立项评估。

## 实施纪要（开发过程中的关键修正）

以下为实施阶段对原设计的修正与补充，均已落地并验证：

1. **SQLite 表结构改为 Flyway 基线管理（不再用 ddl-auto=update）**。实测发现 Hibernate 的 schema migrator 在既有 SQLite 库上批量读取表元数据时会生成超出 `SQLITE_LIMIT_COMPOUND_SELECT` 的复合 SELECT（"too many terms in compound SELECT"），升级 Hibernate 6.6 亦未解决。方案：personal profile 设 `ddl-auto: none` + 通过 Hibernate schema-export 生成完整基线脚本 `db/migration/sqlite/V20260723002__Schema_baseline.sql`（104 表 + 25 索引）由 Flyway 创建。新库建表与既有库启动均验证通过。
2. **XACT_ABORT 陷阱（迁移导入的重大缺陷修正）**。mssql-jdbc 默认 `SET XACT_ABORT ON`：在导入事务中对无 IDENTITY 列的表盲目执行 `SET IDENTITY_INSERT` 会报错并**静默回滚整个事务**（包括此前全部清空操作），导致后续写入出现诡异的唯一键冲突。修正：仅当 JDBC 元数据（IS_AUTOINCREMENT）确认表有 IDENTITY 列时才开启。
3. **`hibernate.default_catalog` 锁定**。共享 SQL Server 实例上存在其他同名表数据库时，schema migrator 的元数据读取会跨库误判"表已存在"（0 CREATE、全 ALTER 失败）。新增 `HibernateCatalogConfig` 从 JDBC URL 提取 databaseName 并锁定 catalog。
4. **`SchemaBootstrapConfig`**：ddl-auto 之前自动 `CREATE SCHEMA`（新空库上 mdr schema 不存在会静默失败）。
5. **Hibernate 升级到 6.6.11.Final**（根 pom 覆盖 `hibernate.version`）。
6. **实体可移植性清理**：移除 12 处 `@Column(columnDefinition = "NVARCHAR(MAX)")`（改用 @Lob 由方言决定）；`ContextTestDataInitializer`、`MetadataSearchService` 的 `mdr.` 硬编码与 `TOP 1`/`OFFSET-FETCH` T-SQL 语法改为方言感知实现；SQLite 的 UUID 以 BLOB 存储，新增 `DatabaseEnvironment.toUuid/toJdbcUuid` 处理 byte[] 与字符串互转。
7. **SQLite 连接与驱动细节**：sqlite-jdbc 不在 Spring Boot `DatabaseDriver` 枚举中，personal profile 必须显式 `driver-class-name: org.sqlite.JDBC`；WAL 不可经 `connection-init-sql` 设置（autoCommit=false 下报 "cannot change into wal mode from within a transaction"），改用 URL 查询参数；personal profile 显式指定 `SQLiteDialect`。
8. **迁移格式**：时间列统一序列化为 `{"$ts": epochMillis}`（兼容 SQLite 以 INTEGER/文本存时间、目标为 datetime2 的跨库场景）；BLOB 列 base64；UUID 按目标方言还原。
9. **达梦落地实录（第一类支持验证通过）**：DM8 实例（Docker `sizx/dm8`，PAGE_SIZE=16/CHARSET=1/CASE_SENSITIVE=0）全量验证通过。关键修正：① 自研 `DmOracleDialect`（Oracle 兼容 + `identity(1,1)` 语法 + BIGINT DDL 类型注册）——DM 不接受 ANSI `generated as identity`，官方 `DmDialect-for-hibernate5.0` 面向 Hibernate 5 不可用；② `context` 为 DM 保留字，DM 部署需 `globally_quoted_identifiers=true`；③ DM 在 schema 内物化 SREF_CON_TAB* 约束辅助表，清理/导出时必须过滤；④ DM JDBC 驱动不支持绑定 `java.util.UUID`，RAW(16) 列统一以 byte[16] 绑定（`DatabaseEnvironment.toJdbcUuid` 按方言分流）；⑤ DM 用户默认无建表权限，需 `GRANT RESOURCE + UNLIMITED TABLESPACE`。
