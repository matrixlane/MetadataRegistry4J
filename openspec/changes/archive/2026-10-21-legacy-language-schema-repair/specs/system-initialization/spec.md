# system-initialization Delta

## ADDED Requirements

### Requirement: 遗留漂移库启动修复先于静态预置

启动期静态预置数据（`StaticDataPresetService.presetAll()`，含语言预置重放）执行之前，系统 MUST 先完成遗留漂移 SQLite 库的 `language_identification` 修复（若该库命中漂移签名，判定与修复语义见 `legacy-schema-repair` 能力）。修复器与静态预置 Runner 的执行顺序 MUST 显式保证（修复器 `@Order` 先于静态预置 Runner），不得依赖 Runner 注册顺序的隐式行为。该顺序保证 MUST 使初始化向导的语言数据源在任何来源的库（全新库、迁移库、遗留漂移库）上均可用于向导治理配置。

#### Scenario: 遗留漂移库初始化向导语言可用

- **GIVEN** 一个遗留漂移 SQLite 库（`language_identification` 含 `language_id NOT NULL` 等遗留列，语言表为空）
- **WHEN** 应用启动完成（修复器先于静态预置执行）并进入初始化向导
- **THEN** `/api/v1/languages` 返回 22 条内置语言（含 `zh-CN`"简体中文"），向导"文档语言"可选择

#### Scenario: 全新库顺序不受影响

- **GIVEN** 一个全新 SQLite 空库（FreshDatabaseInitializer FRESH 路径）
- **WHEN** 应用启动完成
- **THEN** 修复器判定不命中直接跳过，静态预置按既有顺序执行，初始化流程与 sqlite-empty-db-bootstrap 行为一致

