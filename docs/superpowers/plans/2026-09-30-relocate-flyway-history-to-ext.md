# 实现计划：Flyway schema 历史表迁出 mdr，归位 ext（2026-09-30）

依据：`openspec/changes/relocate-flyway-history-to-ext/{proposal,design,tasks}.md`、
Superpowers 设计稿 `docs/superpowers/specs/2026-09-30-relocate-flyway-history-to-ext-design.md`。

## TDD 顺序

1. **红**：新增 `FlywayHistoryRelocatorTest`（测试类引用尚不存在的
   `FlywayHistoryRelocator`），运行确认编译失败。
2. **绿**：实现 `FlywayHistoryRelocator` 与 `FlywayHistoryRelocationConfig`
   （BFPP），测试转绿。
3. **配置**：`application-team.yml` 增 `default-schema`。
4. **回归**：web 模块全量测试 + SQLite scratch 冒烟。

## 变更文件

| 文件 | 动作 |
| --- | --- |
| `metadata-registry-web/src/main/java/.../service/database/FlywayHistoryRelocator.java` | 新增 |
| `metadata-registry-web/src/main/java/.../config/FlywayHistoryRelocationConfig.java` | 新增（BFPP 挂接） |
| `metadata-registry-web/src/test/java/.../service/database/FlywayHistoryRelocatorTest.java` | 新增 |
| `metadata-registry-web/src/test/java/.../config/FlywayHistoryRelocationConfigTest.java` | 新增（BFPP 单测） |
| `metadata-registry-web/src/main/resources/application-team.yml` | 修改（default-schema + 注释） |

## 实现要点

### FlywayHistoryRelocator（web/service/database）

- 构造注入 `DataSource` + `DatabaseEnvironment`；`@PostConstruct` 执行
  `relocate()`（BFPP 保证先于 `FlywayMigrationInitializer` 构造）。
- `vendor() != SQLSERVER` → 直接返回（SQLite/达梦 no-op，不发任何 JDBC）。
- SQL Server 路径（全部参数化查询，仅最终 DDL 拼接可信的配置 schema 名）：
  1. `INFORMATION_SCHEMA.TABLES` 查 ext 是否已有 flyway_schema_history；
  2. 查 mdr 是否有；
  3. 两表并存 → WARN 跳过；仅 mdr 有而 ext schema 不存在（
     `INFORMATION_SCHEMA.SCHEMATA`）→ WARN 跳过；
  4. 仅 mdr 有 → `ALTER SCHEMA <ext> TRANSFER <mdr>.flyway_schema_history`；
  5. 任何 SQLException → 抛出 `IllegalStateException` **fail fast**
     （不能让 Flyway 在缺历史表的情况下启动，否则 re-baseline 重放）。
- 连接经 `DataSourceUtils.getConnection/releaseConnection`（仿 DatabaseCleaner）。

### FlywayHistoryRelocationConfig（web/config）

- 静态 `@Bean` BFPP：`containsBeanDefinition("flywayMigrationInitializer")`
  为真时把 `flywayHistoryRelocator` 并入其 dependsOn（LinkedHashSet 去重、
  保留既有边）；flyway 关闭时 bean 不存在，自然跳过。

### application-team.yml

- `spring.flyway` 下新增：
  `default-schema: ${FLYWAY_DEFAULT_SCHEMA:ext}`
- 注释修订：历史表是工具表 MUST 归 ext；default-schema 兼为迁移会话
  current schema（基线后迁移均已限定，不受影响）；既有库由
  FlywayHistoryRelocator 零重放搬迁；达梦逃逸口 FLYWAY_DEFAULT_SCHEMA。

## 验证

- `./mvnw -pl metadata-registry-web test`（或 `mvn`）：全量 0 failures，
  重点 `FlywayHistoryRelocatorTest`、`FlywayHistoryRelocationConfigTest`、
  `DatabaseCleanerTest`、`FactoryResetServiceTest` 不变绿。
- SQLite scratch 冒烟：personal profile 指向 scratch 副本库，应用正常启动、
  顶层 flyway_schema_history 保留、无搬迁日志。
- SQL Server 真实库（交付后由用户环境验证）：ext 表行数 = 旧 mdr 行数；
  `OBJECT_ID('mdr.flyway_schema_history') IS NULL`；启动日志无 re-baseline。
- `detect_changes` 无 partial/truncated（提交前，AGENTS.md）。

## 显式不做

- 不改 `DatabaseCleaner`/`MigrationService`/`InitWizardService`/
  `FactoryResetService`/`DatabaseEnvironment`。
- 不改任何 Flyway 迁移脚本；不给 `EXT_TABLES` 加 flyway 表。
- 不提交 git（沿用"按 OpenSpec 变更分组、用户指令提交"惯例）。
