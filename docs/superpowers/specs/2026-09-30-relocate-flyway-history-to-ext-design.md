# 设计：Flyway schema 历史表迁出 mdr，归位 ext（2026-09-30）

## 背景与问题

团队版（SQL Server，`metadata_registry` 库）当前 Flyway 配置 `schemas: mdr,ext`，
schema 历史表落在首个 schema，即 `[metadata_registry].[mdr].[flyway_schema_history]`。
该表是 Flyway 工具表，**不属于 ISO/IEC 11179 标准定义的内容**，与标准表同居
`mdr` 破坏了"mdr 只装标准表、ext 装扩展/工具内容"的口径（与 `DatabaseEnvironment`
EXT 清单、`orm/orm-team.xml` 清单的划分原则一致）。

代码中所有业务操作（迁移导出 `MigrationService`、`DatabaseCleaner` 清空、
初始化向导 `InitWizardService`、恢复出厂 `FactoryResetService`）均已按**裸表名**
排除/保留该表，功能层面无缺陷；本次要解决的纯粹是 schema 归属。

## 关键约束（已核实证据）

1. Flyway 的 `defaultSchema`（未设置时取 `schemas[0]`）同时决定两件事：
   ① schema 历史表的落位 schema；② 迁移执行时会话的 current schema
   （Flyway 官方文档明示 "This schema will also be set as the current schema
   on the database connection"）。二者无法独立配置。
2. **只改配置不搬表 = 事故**：配置指向 ext 后，Flyway 在 ext 找不到历史表，
   既有非空库触发 re-baseline，会把 `V20260919001`..`V20260929001` 全部 15 个
   基线后迁移重放一遍。这些脚本按"已应用不重跑"设计（V20260929001 头注释原文
   "本脚本面向既有库一次性迁移（Flyway 已应用不重跑）"），其中
   V20260926001 会 `CREATE UNIQUE INDEX`——重放有失败风险。**故此路排除**。
3. 已逐一核查 15 个基线后迁移（V20260919001..V20260929001），全部 schema 限定
   （V20260926001 的 `CREATE UNIQUE INDEX ... ON mdr.language_identification`
   随表落位 mdr，且带 `IF NOT EXISTS` 守卫）。因此会话 current schema 变为 ext
   后，这些脚本的执行落位不受影响。
4. 基线前老迁移（V20260513001..V20260901014，29 个，存在未限定 DDL）只在
   "全新空库全量重放"路径执行；而团队版全新安装本就不是该路径
   （Hibernate `ddl-auto=update` + `default_schema=mdr` 先建表，全量重放必然撞
   "表已存在"；`.superpowers-memory` 亦记录全新库全量重放既有阻塞项）。
   `baseline-on-migrate` + `baseline-version=20260918002` 保证基线前迁移
   **永不重放**。
5. 搬运手法有项目先例：V20260901014 用
   `ALTER SCHEMA ext TRANSFER mdr.<table>` 整体迁移 23 张表并保留约束/索引；
   该语句对 flyway_schema_history 同样适用（表、PK、
   `flyway_schema_history_s_idx` 索引、installed_rank 等行数据整体搬迁）。

## 方案（用户已确认：方案 A）

**配置指向 ext + 启动期自动搬运器**，既有库零重放、全新库直接落位。

### 1. 配置：`application-team.yml`

```yaml
  flyway:
    locations: classpath:db/migration/${DB_VENDOR:sqlserver}
    schemas: ${FLYWAY_SCHEMAS:mdr,ext}
    default-schema: ${FLYWAY_DEFAULT_SCHEMA:ext}
```

- `schemas` 集合（mdr, ext）不变；`default-schema` 决定历史表落位。
- 注释同步修订：说明历史表属工具表故归 ext；既有库由
  `FlywayHistoryRelocator` 在 Flyway 启动前整体搬入（零重放）；达梦
  "用户即模式"，按需用 `FLYWAY_DEFAULT_SCHEMA` 覆盖（沿用 `FLYWAY_SCHEMAS`
  的既有逃逸模式）。
- personal（SQLite）profile 显式 `schemas: []`，无 schema 概念，不引入该键。

### 2. 新组件 `FlywayHistoryRelocator`

位置：`io.github.matrixlane.metadataregistry.web.service.database`
（与 `DatabaseEnvironment`/`DatabaseCleaner` 同包，数据库基础设施口径）。

职责（仅在 `DatabaseEnvironment.vendor() == SQLSERVER` 时执行，其余方言 no-op）：

```
若 OBJECT_ID('<ext>.flyway_schema_history') IS NULL
   且 OBJECT_ID('<mdr>.flyway_schema_history') IS NOT NULL
   且 ext schema 存在：
    ALTER SCHEMA <ext> TRANSFER <mdr>.flyway_schema_history
若两表同时存在（异常态）：跳过，打 WARN，交人工处理（不做行合并）。
```

- schema 名一律取 `DatabaseEnvironment.extSchema()` / `schema()`，
  尊重 `DB_EXT_SCHEMA` / `DB_DEFAULT_SCHEMA` 覆盖。
- 裸 JDBC 执行（`DataSourceUtils` 取连接，仿 `DatabaseCleaner`），不挂业务事务。
- 仅操作 flyway 表，与 Hibernate ddl-auto / 业务表无交互时序要求。

### 3. 启动顺序挂接：BFPP（仿 `FlywayAfterJpaConfig`）

新增 `@Configuration`（如 `FlywayHistoryRelocationConfig`，置于 `web/config`）：

- 静态 `@Bean` `BeanFactoryPostProcessor`：若存在
  `flywayMigrationInitializer` bean 定义，把 `flywayHistoryRelocator`
  追加进其 `dependsOn`；flyway 关闭（如 local profile）时 bean 不存在，自然跳过。
- 搬运器在 `@PostConstruct`（或 afterPropertiesSet）执行——bean 因 dependsOn
  先于 `FlywayMigrationInitializer`（其 `afterPropertiesSet` 触发
  `flyway.migrate()`）完成构造，从而保证**搬运先于 Flyway 的任何探测**。

### 4. 显式设计决策

- **不把 `flyway_schema_history` 加入 `DatabaseEnvironment.EXT_TABLES`**：
  EXT_TABLES 当前与 `orm/orm-team.xml` 清单一致（OrmTeamXmlTest 守卫），
  而 flyway 表不是 ORM 实体；业务代码对它的保留/排除全部按裸表名进行，
  不走路由清单。保持清单语义纯净。
- **不新增 Flyway 迁移脚本**：搬运由启动期组件完成；迁移脚本方案（方案 B）
  会被 re-baseline 抢先，已排除。
- **不做历史行合并/清洗**：`ALTER SCHEMA TRANSFER` 整体搬迁，installed_rank、
  installed_on、checksum 等原样保留。
- **DatabaseCleaner / MigrationService / InitWizardService / FactoryResetService
  零改动**：均按裸表名 preserve/exclude，与表所在 schema 无关。

## 影响面

| 对象 | 影响 |
| --- | --- |
| 既有团队库（SQL Server） | 下次启动自动搬运；Flyway 在 ext 找到完整历史，validate 通过，只应用未来新迁移 |
| 全新团队库 | Hibernate 建表 → 基线化 → 历史表直接建于 ext；mdr 从第一天起只含标准表 |
| SQLite（personal） | 无 schema 概念；配置不引入 default-schema；搬运器 no-op |
| 达梦（team profile 切换） | 用户即模式；历史表本就在连接用户下；搬运器 no-op；按需 `FLYWAY_DEFAULT_SCHEMA` 覆盖 |
| 迁移导出/导入 | 导出仍按裸表名排除；导入目标库无论历史表在 mdr 还是 ext 都按名保留 |

## 测试与验证

1. 新增 `FlywayHistoryRelocatorTest`（mock JDBC，仿 `DatabaseCleanerTest` 风格）：
   - ext 缺 + mdr 有 → 执行 `ALTER SCHEMA ext TRANSFER mdr.flyway_schema_history`
   - 两表并存 / 仅 ext 有 → 不执行 ALTER（异常态 WARN）
   - SQLite / 达梦 vendor → 不发出任何语句
   - `DB_DEFAULT_SCHEMA` / `DB_EXT_SCHEMA` 自定义值被尊重
2. `metadata-registry-web` 模块全量测试零回归。
3. SQLite scratch 副本冒烟：personal 路径行为不变。
4. SQL Server 真实库（用户环境 8081）：重启后核对
   - `SELECT COUNT(*) FROM ext.flyway_schema_history` = 旧 mdr 表行数
   - `SELECT OBJECT_ID('mdr.flyway_schema_history')` 为 NULL
   - 启动日志无 re-baseline / 无迁移重放；后续新增迁移正常应用。
5. 提交前 `detect_changes` 无 partial/truncated（AGENTS.md 规则）。

## OpenSpec 变更（下一阶段产出）

- 变更名：`relocate-flyway-history-to-ext`
- 主 spec delta：能力 `system-initialization` 新增需求——Flyway schema 历史表
  位于 ext schema（既有库升级自动搬迁且零重放；全新库直接建于 ext；
  SQLite/达梦不受影响；清理/导出保留语义不变）。
- 既有需求"初始化向导清理保留静态预置表"中 flyway_schema_history 的
  保留语义不变，仅位置变化，不需修改原文。

## 遗留与风险

- 两表并存异常态：跳过搬运 + WARN 日志，人工处理（设计取舍：不自动合并，
  避免在 Flyway 管理的表上做行级决策）。
- 达梦默认值未实测（项目既有状态：达梦多迁移待实测，与本变更无关的既有项）。
