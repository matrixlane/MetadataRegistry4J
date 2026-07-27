## Purpose

本规格描述 `identification` 能力域在清理 `IdentifierType` 后的目标状态：`ScopedIdentifier` 与策略配置只与 `Namespace` 绑定，路由按 Namespace 单键解析，数据库表不再保留 `identifier_type` 与 `effective_strategy_id` 列。

## ADDED Requirements

### Requirement: ScopedIdentifier 不应包含 identifierType 字段
ISO/IEC 11179-3:2023 Clause 7（Identification）定义的 `Scoped_Identifier` 不包含"标识符类型"作为分类属性。本规约规定 `ScopedIdentifier` 实体 SHALL 不包含 `identifierType` 字段。

#### Scenario: ScopedIdentifier.java 中不存在 identifierType
- **WHEN** 开发者阅读 `ScopedIdentifier.java` 实体类
- **THEN** 不存在 `identifierType` 字段、Javadoc 不引用 `IdentifierType` 枚举

#### Scenario: 数据库 scoped_identifier 表中不存在 identifier_type 列
- **WHEN** 执行 Flyway 迁移到最新版本
- **THEN** `scoped_identifier` 表中不存在 `identifier_type` 列

#### Scenario: 全局代码无 IdentifierType 引用
- **WHEN** 在项目源代码中搜索 `IdentifierType`
- **THEN** 唯一命中是 `IdentifierType.java` 文件本身（待删除），无其他引用

### Requirement: ScopedIdentifier 不应包含 effectiveStrategyConfig 字段
本规约规定 `ScopedIdentifier` SHALL 不直接持有 `IdentifierStrategyConfig` 引用。策略版本化机制由 `IdentifierStrategyConfig` 自身版本号维护，调用方可通过 `(namespace_id, created_at)` 关联到创建时的策略版本。

#### Scenario: ScopedIdentifier.java 中不存在 effectiveStrategyConfig
- **WHEN** 开发者阅读 `ScopedIdentifier.java` 实体类
- **THEN** 不存在 `effectiveStrategyConfig: IdentifierStrategyConfig` ManyToOne 字段

#### Scenario: 数据库 scoped_identifier 表中不存在 effective_strategy_id 列
- **WHEN** 执行 Flyway 迁移到最新版本
- **THEN** `scoped_identifier` 表中不存在 `effective_strategy_id` 列

### Requirement: IdentifierGenerationStrategyRegistry 路由应基于 Namespace 单键
策略解析 SHALL 简化为基于 `Namespace` 单键查询，不再需要 `IdentifierType` 维度。

#### Scenario: Registry.resolve 方法签名只接收 Namespace
- **WHEN** 调用 `IdentifierGenerationStrategyRegistry.resolve(namespace)`
- **THEN** 返回一个有效的 `IdentifierGenerationStrategy` 实例（按 namespace 特定策略 → global default 顺序解析）

#### Scenario: 现有业务调用编译通过
- **WHEN** `ItemAdministrationService` 调用 `registry.resolve(namespace)`（无 type 参数）
- **THEN** 项目编译通过、单元测试通过

### Requirement: IdentifierStrategyConfig 不应包含 identifierType 字段
策略配置实体 SHALL 不包含 `identifierType` 字段。策略与 Namespace 单维绑定。

#### Scenario: IdentifierStrategyConfig.java 中不存在 identifierType
- **WHEN** 开发者阅读 `IdentifierStrategyConfig.java`
- **THEN** 不存在 `identifierType` 字段

#### Scenario: 数据库 identifier_strategy_config 表中不存在 identifier_type 列
- **WHEN** 执行 Flyway 迁移到最新版本
- **THEN** `identifier_strategy_config` 表中不存在 `identifier_type` 列

#### Scenario: 唯一约束简化为 (namespace_id, version)
- **WHEN** 在同一 namespace 下创建两个 IdentifierStrategyConfig
- **THEN** 数据库唯一约束为 `uk_strategy_namespace (namespace_id, version)`，允许不同 namespace 有相同 version 号

### Requirement: SequenceRegistry 不应包含 identifierType 字段
序列注册表 SHALL 不包含 `identifier_type` 维度。每个 Namespace 一个序列。

#### Scenario: identifier_sequence_registry 表中不存在 identifier_type 列
- **WHEN** 执行 Flyway 迁移到最新版本
- **THEN** `identifier_sequence_registry` 表中不存在 `identifier_type` 列

#### Scenario: 同一 namespace 下只能有一个序列记录
- **WHEN** 在同一 namespace 下尝试创建第二个 SequenceRegistry
- **THEN** 数据库唯一约束 `uk_seq_registry_namespace` 阻止该操作

### Requirement: 数据迁移保证 SI 标识符值不变
清理 identifier_type 列时，SHALL 保留所有现有 ScopedIdentifier 的 `identifier` 字符串值，不做任何业务语义变更。

#### Scenario: 旧库升级后 SI identifier 值保持
- **WHEN** 对包含 `identifier_type='URN'` 记录的旧库执行 Flyway 迁移
- **THEN** 迁移完成后，所有 SI 的 `identifier` 字段值与迁移前完全一致

#### Scenario: URN 格式 identifier 可正常解析
- **WHEN** 通过 `urn:xxx:uuid` 格式的 identifier 查找 SI
- **THEN** 系统能正确匹配（identifier 值本身的 URN 格式已包含 type 语义）

### Requirement: OpenSpec 历史同步
archive 目录中引用 IdentifierType 的历史变更 SHALL 标注清理指向，避免后人混淆。

#### Scenario: archive proposal.md 顶部包含清理指向
- **WHEN** 阅读 `openspec/changes/archive/2026-06-01-auto-generate-scoped-identifiers/proposal.md`
- **THEN** 顶部包含"Note (2026-07-27)"段落，指向本清理变更
