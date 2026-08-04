# identification Specification

## Purpose
TBD - created by archiving change cleanup-identifier-type-and-simplify-strategy-routing. Update Purpose after archive.
## Requirements
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

### Requirement: 初始化时命名空间级标识与版本生成策略配置
初始化向导创建默认命名空间时，系统 SHALL 同步采集并创建该命名空间的标识生成策略配置（`IdentifierStrategyConfig`：策略类型 UUID/SEQUENCE/TEMPLATE 及前缀、模板参数），状态 MUST 为 PUBLISHED 且立即生效；同时 SHALL 将版本生成策略（如 SEMVER/SEQUENTIAL/DATE）持久化为命名空间级设置（`Namespace.versioningPolicy`，默认 SEMVER）。跳过命名空间创建时 MUST NOT 创建策略配置。注册规范 MUST NOT 承载标识/版本生成策略字段。

#### Scenario: 创建命名空间时策略配置同步落库
- **WHEN** 管理员在初始化向导中创建默认命名空间并选择 SEQUENCE 标识策略与前缀、SEMVER 版本策略
- **THEN** 系统 SHALL 创建命名空间级 `IdentifierStrategyConfig`（PUBLISHED、version=1）并将 `versioningPolicy` 写入命名空间，新策略立即参与标识符生成

#### Scenario: 跳过命名空间时不创建策略配置
- **WHEN** 管理员在初始化向导中跳过命名空间设置
- **THEN** 系统 MUST NOT 创建任何命名空间级 `IdentifierStrategyConfig`

### Requirement: 自有数据标准的命名空间前置条件
在注册机构下创建自有数据标准（注册受管项并在命名空间下生成 `Scoped_Identifier`）前，系统 MUST 存在至少一个与该注册机构关联的命名空间。缺失时系统 SHALL 阻止创建操作并返回业务校验错误，错误信息引导用户先创建命名空间；前端 SHALL 禁用相关创建入口并展示补建引导。导入或访问外部组织数据标准（外部命名空间，无关联注册机构）MUST NOT 受此限制。系统内部命名空间（system-default）SHALL NOT 计入该前置条件。

#### Scenario: 无注册机构关联命名空间时创建自有数据标准被阻止
- **WHEN** 用户在系统中尚无任何与注册机构关联的命名空间时，尝试注册受管项或创建自有数据标准
- **THEN** 系统 SHALL 拒绝该操作并返回业务校验错误，提示必须先创建基于注册机构的命名空间

#### Scenario: 补建命名空间后放行
- **WHEN** 用户在初始化时跳过了命名空间设置，之后创建了基于注册机构的命名空间
- **THEN** 系统 SHALL 允许其创建自有数据标准

#### Scenario: 导入外部数据标准不受限
- **WHEN** 系统中不存在与注册机构关联的命名空间，用户导入外部组织的数据标准（使用外部命名空间）
- **THEN** 系统 SHALL 正常完成导入，不要求先创建命名空间

