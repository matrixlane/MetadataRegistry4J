# relocate-flyway-history-to-ext（2026-10-01）

## 起因

用户指出 `[metadata_registry].[mdr].[flyway_schema_history]` 不属于 MDR 标准规范
（ISO/IEC 11179）定义的内容，应按 EXT schema 处理——该表是 Flyway 工具表，却与
标准表同居 `mdr`，破坏"mdr 只装标准表"的口径。走 superpowers-openspec-superpowers
工作流，用户两轮确认（方案 A、tasks.md）后交付。

## 方案 A（用户确认）：default-schema 指 ext + 启动期自动搬运器

关键证据（设计阶段核实，详见
`docs/superpowers/specs/2026-09-30-relocate-flyway-history-to-ext-design.md`）：

- Flyway `defaultSchema` 一箭双雕：历史表落位 + 迁移会话 current schema，
  无法独立配置。
- 只改配置不搬表 → 既有库 re-baseline → 重放全部 15 个基线后迁移
  （按"已应用不重跑"设计，会炸）——方案 B 排除。
- 15 个基线后迁移逐一核查全部 schema 限定（V20260926001 的 UNIQUE INDEX
  随表落位 mdr 且带守卫）；基线前 29 个老迁移只在"全新空库全量重放"路径执行，
  而团队版全新安装走 Hibernate 建表 + 基线化，全量重放本就不可行——基线机制
  保证老迁移永不重放。
- 搬运手法有 V20260901014 先例（ALTER SCHEMA TRANSFER 保留约束/索引）。

## 交付物

- 配置：`application-team.yml` + `default-schema: ${FLYWAY_DEFAULT_SCHEMA:ext}`。
- 新组件：`FlywayHistoryRelocator`（web/service/database，仅 SQL Server；
  ext 缺+mdr 有+ext schema 存在才 TRANSFER；双表并存 WARN 跳过；异常 fail fast
  防 Flyway re-baseline）+ `FlywayHistoryRelocationConfig`（BFPP 仿
  FlywayAfterJpaConfig，把搬运器并入 flywayMigrationInitializer dependsOn）。
- 显式不做：flyway 表不进 EXT_TABLES（保持与 orm-team.xml 清单一致）；
  无新 Flyway 迁移脚本；DatabaseCleaner/MigrationService/向导/出厂零改动。
- OpenSpec：change `relocate-flyway-history-to-ext`（proposal/design/specs/tasks
  齐备，`openspec validate --strict` 通过），主 spec `system-initialization`
  待归档时新增需求"Flyway schema 历史表位于 ext schema"（4 场景）。

## 验证

- 单测 12+3 全绿（mock JDBC）。期间发现并修复 Mockito 未完成桩写法
  （thenReturn 参数内联套打别的 mock）。
- web 全量 1354 用例：失败集（10）= 干净 HEAD 基线失败集（12）的真子集
  （WorkspaceServiceIntegrationTest 既有失败 + 并发类时序抖动；基线多出
  ScopedIdentifierConcurrency 2 个 flake）——**零回归**。
- 主树被并行流程（remove-individual-nonstandard-fields，删 avatar）打断编译
  期间，全部验证改在隔离 worktree（.worktrees/flyway-history-ext，HEAD+本变更）
  与基线 worktree（flyway-history-base）做对跑。
- SQLite 冒烟（scratch/flyway-history-ext-smoke/smoke.sh，worktree 跑应用）：
  3/3 PASS——副本库启动正常、flyway_schema_history 顶层保留、行数只增、
  baseline 行数不变（零 re-baseline）、日志无 ALTER SCHEMA。

## 遗留

- 3.4 SQL Server 真实库核对待用户部署后执行：`ext.flyway_schema_history` 行数=
  旧 mdr 表行数；`OBJECT_ID('mdr.flyway_schema_history') IS NULL`；启动日志无
  re-baseline/无基线后迁移重放；之后新增迁移正常应用。
- 提交时按 OpenSpec 变更分组，先跑 detect_changes（本次会话跑过一次，
  含并行流程改动）。
- 验证用 worktree（flyway-history-ext / flyway-history-base）已用完可删。

## 代码评审轮（同日，独立评审子代理）

评审抓出 2 BLOCKER + 2 MAJOR（全部修复并复验）：

- **BLOCKER-1（已核实）**：BFPP 按字面量 `flywayMigrationInitializer` 查 bean，
  Boot 3.5 实际 bean 名为 `flywayInitializer`（反编译 jar 证实）→ BFPP 恒定
  静默 no-op。修复：改 `getBeanNamesForType(FlywayMigrationInitializer.class)`
  类型查找；同修正 `FlywayAfterJpaConfig` 中的同款失效字面量（今日无行为变化，
  防 Boot 升版漂移）。新增 `FlywayHistoryRelocationWiringTest`
  （ApplicationContextRunner + 真实 FlywayAutoConfiguration）钉死接线与
  default-schema 绑定。
- **BLOCKER-2（已核实）**：application.yml 连接池 `auto-commit: false`，
  Hikari 在 close 时对脏连接 rollback——搬迁 DDL 会被静默回滚（日志却报成功）。
  修复：搬迁前 `setAutoCommit(true)`、执行后恢复原值。
- **MAJOR-1**：搬迁目标原取 `DB_EXT_SCHEMA`，与 `FLYWAY_DEFAULT_SCHEMA` 可
  分叉导致"搬去 Flyway 找不到的 schema"。修复：目标取自
  `Flyway.getConfiguration()`（defaultSchema → schemas[0] 回退），与 Flyway
  落位永不分叉。
- **MAJOR-2**：测试把上述缺陷固化成假绿。修复：单测重写（13 用例，含
  setAutoCommit 断言、fail-fast 断言、Flyway 配置跟随断言）+ 接线测试。
- MINOR 一并处理：目标 schema 缺失由 WARN 改 fail fast；探测从
  INFORMATION_SCHEMA SQL 改 DatabaseMetaData（与 DatabaseCleaner 同 idioms，
  也满足"业务代码禁方言专有 SQL"规则）；标识符双引号限定（同 qualify()）；
  getConnection 失败包装；回滚操作步骤落入 docs/technical/database-portability.md。

评审验证（worktree，HEAD=3046678）：目标测试 17/17 绿；web 全量 1365 用例
失败集（10）= 初版失败集 = 干净 HEAD 基线（12）的真子集，零回归。
注意：多模块验证必须 `-am`（并行流程反复 install common-facilities jar 到
~/.m2，单模块 -pl 会对错 jar 编译）。
