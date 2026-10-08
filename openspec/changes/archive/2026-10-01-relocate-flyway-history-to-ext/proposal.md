# 提案：Flyway schema 历史表迁出 mdr，归位 ext

## Why

团队版（SQL Server）的 Flyway schema 历史表当前落在首个 schema，即
`[metadata_registry].[mdr].[flyway_schema_history]`。该表是 Flyway 工具表，
不属于 ISO/IEC 11179 标准定义的内容，与标准表同居 `mdr`，破坏了
"mdr 只装标准表、ext 装扩展/工具内容"的项目口径（与 `DatabaseEnvironment`
EXT 清单、`orm/orm-team.xml` 清单的划分原则一致）。需要将其迁出 `mdr`，
按 EXT schema 处理。

## What Changes

- Flyway 配置（团队版）显式指定 `default-schema: ext`：schema 历史表落于
  ext schema；既有库由启动期搬运器在 Flyway 运行前把
  `mdr.flyway_schema_history` 整体迁入 ext（`ALTER SCHEMA ... TRANSFER`，
  保留全部历史行与索引），**零重放、零 re-baseline**。
- 新增 `FlywayHistoryRelocator` 组件及启动顺序挂接（BFPP，仿
  `FlywayAfterJpaConfig`），仅 SQL Server 方言生效；两表并存异常态跳过并
  WARN，不自动合并。
- 全新团队库的历史表自始建于 ext；`mdr` 从第一天起只含标准表。
- SQLite（无 schema 概念）与达梦（用户即模式）不受影响；达梦沿用
  `FLYWAY_SCHEMAS` 的逃逸模式，可用 `FLYWAY_DEFAULT_SCHEMA` 覆盖。
- 非破坏性变更：业务代码对历史表的保留/排除全部按裸表名进行，不随位置改变。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `system-initialization`: 新增需求——Flyway schema 历史表位于 ext schema
  （既有库升级启动时自动整体搬迁且迁移零重放；全新团队库直接建于 ext；
  SQLite/达梦不受影响；清理/导出对历史表的保留语义不变）。既有需求原文
  不修改。

## Impact

- 配置：`metadata-registry-web/src/main/resources/application-team.yml`
  （`default-schema` 键与注释修订）。
- 新代码：`FlywayHistoryRelocator`（`web/service/database` 包）+
  启动顺序 `BeanFactoryPostProcessor`（`web/config`）+ 单测。
- 无新增 Flyway 迁移脚本；`DatabaseCleaner`/`MigrationService`/
  `InitWizardService`/`FactoryResetService` 零改动。
- 既有团队库（SQL Server）下次应用启动时自动完成搬迁；
  个人版（SQLite）与达梦部署行为不变。
