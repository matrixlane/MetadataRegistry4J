# system-initialization 增量规格

## ADDED Requirements

### Requirement: 静态预置数据的统一定义与时机
系统 SHALL 将全部静态预置数据收敛到单一幂等入口 `StaticDataPresetService.presetAll()`，数据值仅以 SQL 预置脚本为数据源（2026-09-19 用户裁定：应用代码 MUST NOT 包含名称预置数据、不做运行时 Locale 解析）。静态预置集合 MUST 包含：G20 语言代码（22 条，固定 UUID）、语言中文名称与地区名称（22 条）、系统组织与 `sys:` 系统默认命名空间（含中英双指称）、`identifier_strategy_config` 全局默认行（`UUID`/`INTERNAL`/`is_global_default=1`）、占位超级管理员（`admin` / `NO_PASSWORD`）、每注册机构的标准注册流转规则（调和语义，不覆盖用户定制规则）。`presetAll()` MUST 幂等：连续执行两次结果集不变。

#### Scenario: 数据库设置完成后静态数据全部就绪
- **WHEN** 管理员在向导数据库步骤保存数据源配置并重启
- **THEN** Flyway 完成建表与迁移后，`presetAll()` MUST 在应用就绪前执行完毕，静态预置集合中的全部数据可用

#### Scenario: 幂等重复执行
- **WHEN** `presetAll()` 在同一数据库上连续执行两次
- **THEN** 两张语言表行数、系统组织与命名空间、全局默认策略、占位管理员均保持不变，不产生重复行

#### Scenario: 三方言一致
- **WHEN** 分别在 SQLite、SQL Server、达梦全新库上完成上述流程
- **THEN** 静态预置集合内容一致（方言差异仅限 SQL 语法与 schema 限定）

### Requirement: 静态预置的三处执行时机复用同一入口
`presetAll()` MUST 在三处被调用且顺序一致：应用启动期（Flyway 之后）、恢复出厂设置清空后、初始化向导 `setup()` 清理用户数据后。初始化向导 `setup()` MUST NOT 直接写入任何静态预置数据（不得调用语言初始化、预置重放等步骤的旁路实现）；`FactoryResetService` MUST NOT 保留与统一入口不一致的预置序列。

#### Scenario: 恢复出厂后静态数据完整
- **WHEN** 管理员执行恢复出厂设置
- **THEN** 出厂完成后静态预置集合完整可用，且 `identifier_strategy_config` 全局默认行存在（含 SQL Server 方言）

#### Scenario: 向导 setup 不写静态预置
- **WHEN** 调用 `/api/v1/init-wizard/setup`
- **THEN** 请求处理过程中静态预置数据仅由 `presetAll()` 幂等兜底维护，`setup` 代码路径 MUST NOT 包含语言代码或语言名称的写入逻辑

### Requirement: 初始化向导清理保留静态预置表
`InitWizardService.clearBusinessDataOnly()` 的清理保留集合 MUST 包含纯静态预置表 `language_identification` 与 `ext_language_identification`，与 `account`、`system_setting`、`flyway_schema_history` 一并不被清空；其余业务表允许清空，清空后由 `presetAll()` 幂等兜底恢复静态行。

#### Scenario: setup 清理后语言预置保持
- **WHEN** 管理员重复提交 `setup`（上一次未完成）
- **THEN** 清理完成后 `language_identification` 22 行与 `ext_language_identification` 22 行保持不变（或经 `presetAll()` 兜底后恢复），语言中文名称不丢失

### Requirement: 演示数据与初始化隔离
上下文演示数据（8 条演示 Context）MUST 由独立配置项 `app.seed.demo-data.enabled` 控制，默认值 MUST 为 `false`；为 `false` 时应用启动 MUST NOT 灌入演示数据，初始化向导与恢复出厂设置亦 MUST NOT 产生演示数据。

#### Scenario: 默认配置下无演示数据
- **WHEN** 全新部署使用默认配置完成初始化
- **THEN** `context` 表中不含任何演示上下文数据

#### Scenario: 显式开启后演示数据可用
- **WHEN** 部署方设置 `app.seed.demo-data.enabled=true` 并重启
- **THEN** 应用启动时按既有规则灌入 8 条演示上下文（仅当 context 表为空）

## MODIFIED Requirements

### Requirement: 幂等与可恢复语义保持
初始化向导 MUST 保持现有幂等与可恢复语义：`setup` 可重复提交（自动清理上次未完成的数据）、`abort` 可安全多次调用、系统未提交（commit）前 MUST 保持未初始化状态。该语义在全部受支持方言上 MUST 一致。`setup` 的清理动作 MUST 保留静态预置表（见"初始化向导清理保留静态预置表"），清理后由统一静态预置入口幂等兜底，确保任何情况下静态预置数据完整可用。

#### Scenario: 重复提交 setup
- **WHEN** 管理员完成一次 `setup` 后未提交，又以不同配置再次调用 `setup`
- **THEN** 系统 SHALL 清理上一次写入的业务数据并以新配置重建，不产生重复注册机构或命名空间，且静态预置数据（语言及名称、系统默认命名空间、全局默认策略）保持完整

#### Scenario: 出厂后再次走初始化向导
- **WHEN** 恢复出厂设置后管理员重新执行初始化向导
- **THEN** 向导各步骤可见的语言下拉选项包含中文语言名称，文档语言预填项显示中文名称而非 BCP 47 标识符

#### Scenario: 放弃后重新开始
- **WHEN** 管理员调用 `abort` 后重新进入向导
- **THEN** 系统 SHALL 处于可重新初始化的干净状态，管理员账号密码重置为待设置状态，静态预置数据保持完整
