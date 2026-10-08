# system-initialization 增量规格

## MODIFIED Requirements

### Requirement: 静态预置数据的统一定义与时机
系统 SHALL 将全部静态预置数据收敛到单一幂等入口 `StaticDataPresetService.presetAll()`，数据值仅以 SQL 预置脚本为数据源（2026-09-19 用户裁定：应用代码 MUST NOT 包含名称预置数据、不做运行时 Locale 解析）。静态预置集合 MUST 包含：G20 语言代码（22 条，固定 UUID）、语言中文名称与地区名称（22 条）、`identifier_strategy_config` 全局默认行（`UUID`/`INTERNAL`/`is_global_default=1`）、占位超级管理员（`admin` / `NO_PASSWORD`）、每注册机构的标准注册流转规则（调和语义，不覆盖用户定制规则）。`presetAll()` MUST 幂等：连续执行两次结果集不变。

系统组织与系统默认命名空间均不再是预置数据：系统 SHALL NOT 预置 `sys:` 系统默认命名空间；历史系统组织（固定 UUID `00000000-0000-0000-0000-000000000001`）不再创建，存量库中无引用（命名空间 NamingAuthority、联系人所属组织）的历史系统组织行由数据迁移移除。用户可见的组织列表与组织下拉选项 MUST NOT 包含系统组织。

#### Scenario: 数据库设置完成后静态数据全部就绪
- **WHEN** 管理员在向导数据库步骤保存数据源配置并重启
- **THEN** Flyway 完成建表与迁移后，`presetAll()` MUST 在应用就绪前执行完毕，静态预置集合中的全部数据可用

#### Scenario: 幂等重复执行
- **WHEN** `presetAll()` 在同一数据库上连续执行两次
- **THEN** 两张语言表行数、全局默认策略、占位管理员均保持不变，不产生重复行

#### Scenario: 三方言一致
- **WHEN** 分别在 SQLite、SQL Server、达梦全新库上完成上述流程
- **THEN** 静态预置集合内容一致（方言差异仅限 SQL 语法与 schema 限定）

### Requirement: 幂等与可恢复语义保持
初始化向导 MUST 保持现有幂等与可恢复语义：`setup` 可重复提交（自动清理上次未完成的数据）、`abort` 可安全多次调用、系统未提交（commit）前 MUST 保持未初始化状态。该语义在全部受支持方言上 MUST 一致。`setup` 的清理动作 MUST 保留静态预置表（见"初始化向导清理保留静态预置表"），清理后由统一静态预置入口幂等兜底，确保任何情况下静态预置数据完整可用。

#### Scenario: 重复提交 setup
- **WHEN** 管理员完成一次 `setup` 后未提交，又以不同配置再次调用 `setup`
- **THEN** 系统 SHALL 清理上一次写入的业务数据并以新配置重建，不产生重复注册机构或命名空间，且静态预置数据（语言及名称、全局默认策略）保持完整

#### Scenario: 出厂后再次走初始化向导
- **WHEN** 恢复出厂设置后管理员重新执行初始化向导
- **THEN** 向导各步骤可见的语言下拉选项包含中文语言名称，文档语言预填项显示中文名称而非 BCP 47 标识符

#### Scenario: 放弃后重新开始
- **WHEN** 管理员调用 `abort` 后重新进入向导
- **THEN** 系统 SHALL 处于可重新初始化的干净状态，管理员账号密码重置为待设置状态，静态预置数据保持完整
