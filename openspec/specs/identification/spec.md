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

### Requirement: 所有 Item 子类界面提供统一的标识符/版本编辑区段
系统 SHALL 为所有具有 identified_item 的 Item 子类（包括但不限于 DataElement、DataElementConcept、ObjectClass、Property、ValueMeaning、MeasureClass、UnitOfMeasure、Dimensionality、DataType、DataTypeScheme、ConceptualDomain、ValueDomain、Context、ConstraintSet、NamingConvention）的创建/编辑界面提供与数据元一致的注册区段：命名空间选择、标识符输入（含内嵌的自动/手工切换控件与段模板变量录入）、版本输入（含内嵌的自动/手工切换控件）。"标识符生成方式"与"版本生成方式" SHALL 作为对应输入框的内嵌 UI 元素存在（与输入框视觉复合，承载于 `el-input` 的 `append` 槽位），不得再以独立的表单字段行呈现。该区段 MUST 由同一可复用组件实现，禁止各页面各自维护一份发散的标识符编辑逻辑。编辑态 MUST 按持久化的 `identifierCreationMode` / `versionCreationMode` 决定输入控件与切换控件的状态（AUTO 锁定 / MANUAL 开放 / NULL 等价 MANUAL）。系统 MUST 在响应中通过 `ItemMetaResponse` 暴露这两个字段供编辑态透传与将来 API 检索。RegistrySpecification 不是 Item 子类（ISO/IEC 11179-3:2023 9.4.10 未声明 Direct superclass），其界面 MUST NOT 提供注册区段。ClassificationScheme / ClassificationNode / MappingScheme 不是 Item 子类，其界面不受本要求约束。

#### Scenario: 数据元概念创建界面呈现完整注册区段
- **WHEN** 用户打开数据元概念（DataElementConcept）的新建对话框
- **THEN** 界面呈现命名空间选择、标识符输入（含内嵌自动/手工切换控件与段模板变量项）、版本输入（含内嵌自动/手工切换控件），行为与数据元新建对话框一致

#### Scenario: 非 Item 实体不受影响
- **WHEN** 用户打开 Contact / Organization / Individual / Registrar / DocumentType / ReferenceDocument 的创建/编辑界面
- **THEN** 界面不呈现注册区段，维持现状

#### Scenario: 分类方案界面不属于本能力范围
- **WHEN** 用户打开分类方案或分类节点的创建/编辑对话框
- **THEN** 维持其专有表单（方案名称/节点编码等），不呈现注册区段

#### Scenario: 标识符/版本生成方式不再以独立字段行呈现
- **WHEN** 用户打开任意 Item 子类（如 DataElement、DataElementConcept、ObjectClass 等）的创建/编辑对话框
- **THEN** "标识符生成方式"与"版本生成方式" MUST 作为"标识符"输入框与"版本"输入框的内嵌 UI 元素存在（位于输入框右侧 `append` 槽位），界面中不存在名为"标识符生成方式"或"版本生成方式"的独立表单字段行

#### Scenario: 编辑 AUTO 创建的 Item 时输入控件锁定
- **WHEN** 用户打开一个 `identifierCreationMode='AUTO'` 的 Item 编辑对话框
- **THEN** 标识符输入框禁用、内嵌切换控件不显示；占用位提示"自动生成，不可编辑"

#### Scenario: 注册规范界面无注册区段
- **WHEN** 用户打开注册规范设置页
- **THEN** 界面不提供命名空间/标识符/版本注册区段

### Requirement: 命名空间输入策略在所有 Item 实体上统一生效

系统 MUST 在所有 Item 子类的创建路径按所选命名空间的 `identifierInputMode` 与 `versionInputMode` 决定标识符/版本的自动或手工输入：AUTO_ONLY 时输入框禁用且保存后自动生成；AUTO_WITH_OVERRIDE 时默认自动、可切换手工；MANUAL_ONLY 时必须手工输入。手工标识符 SHALL 在目标命名空间内唯一；手工版本 MUST 通过该命名空间 `versioningPolicy` 对应的校验（如 SemVer）。

#### Scenario: AUTO_ONLY 命名空间下创建值含义
- **WHEN** 用户在 AUTO_ONLY 命名空间下创建 ValueMeaning
- **THEN** 标识符与版本输入框禁用，保存后由系统按命名空间策略生成

#### Scenario: MANUAL_ONLY 命名空间下手工输入非法版本
- **WHEN** 用户在 MANUAL_ONLY 且 versioningPolicy 为 SEMVER 的命名空间下创建任意 Item 并输入非法版本号
- **THEN** 表单校验失败并提示，不提交

#### Scenario: 段模板变量在任意 Item 创建时可用
- **WHEN** 所选命名空间的标识符生成策略为段模板且包含 VARIABLE 段，用户在任意 Item 新建界面选择自动生成
- **THEN** 界面呈现变量输入项，提交时随 `variables` 一并传递

### Requirement: 编辑态标识符/版本语义统一

系统 MUST 在所有 Item 子类的编辑界面中按创建时持久化的 `IdentifierCreationMode`（取值 `AUTO` / `MANUAL`，既存记录为 `NULL`）决定标识符与版本的可编辑性：

- 创建时标识符选择 `AUTO`：编辑态标识符输入框 MUST 禁用、内嵌切换控件 MUST 不显示、提交时携带 `identifierMode='AUTO'` 与空 identifier；后端 MUST 拒绝任何对 identifier 的手动修改。
- 创建时标识符选择 `MANUAL`：编辑态标识符输入框 MUST 启用（沿用单 SI 可改逻辑）、内嵌切换控件可显示、后端允许在 `ai.getScopedIdentifiers().size() == 1` 条件下改写 identifier 并按命名空间 `manualIdentifierPattern` / 已发布模板格式校验。
- 创建时版本选择 `AUTO`：编辑态版本输入框 MUST 禁用、内嵌切换控件 MUST 不显示；提交时携带 `versionMode='AUTO'`；后端 MUST 忽略请求体携带的 version 值，按命名空间 `versioningPolicy` 自动递增。
- 创建时版本选择 `MANUAL`：编辑态版本输入框 MUST 启用、内嵌切换控件可显示；后端接受请求体显式 version 并按命名空间 `versioningPolicy` 校验，未提供则按策略递增。
- `NULL`（迁移前既存 SI）：等价 `MANUAL` 处理，保持当前 UI 与后端语义。
- 创建时若请求体未携带 `identifierMode` / `versionMode`，后端 MUST 按"请求体 identifier / version 是否有显式值"反推（空 → AUTO，非空 → MANUAL），写入 SI。
- mode 字段本身在编辑过程中 MUST 保持不变（创建即锁定，不引入"反向能力"）。

首次为历史遗留无标识的 Item 编辑保存时 SHALL 按所选命名空间策略补录 ScopedIdentifier，并按"identifier 是否有显式值"反推写入对应的 mode。

#### Scenario: 编辑 AUTO 创建的 Item 标识符

- **WHEN** 用户打开一个创建时选择标识符自动生成的 Item 编辑表单
- **THEN** 标识符输入框禁用、内嵌切换控件不显示、占位符提示"自动生成，不可编辑"；提交时携带 `identifierMode='AUTO'` 且后端忽略 identifier 字段

#### Scenario: 编辑 MANUAL 创建的 Item 标识符

- **WHEN** 用户打开一个创建时选择标识符手动输入的 Item 编辑表单
- **THEN** 标识符输入框启用、内嵌切换控件可显示；用户可改写 identifier 并按命名空间校验

#### Scenario: 编辑 AUTO 创建的 Item 版本

- **WHEN** 用户打开一个创建时选择版本自动生成的 Item 编辑表单
- **THEN** 版本输入框禁用、内嵌切换控件不显示；后端忽略请求体 version，按命名空间 `versioningPolicy` 自动递增

#### Scenario: 编辑 MANUAL 创建的 Item 版本

- **WHEN** 用户打开一个创建时选择版本手动输入的 Item 编辑表单
- **THEN** 版本输入框启用；用户显式指定版本时经命名空间版本策略校验后采用，留空则按命名空间版本策略递增

#### Scenario: 编辑存量无 mode 字段的记录

- **WHEN** 用户编辑一个迁移前既存（`identifierCreationMode` 为 `NULL`）的 Item
- **THEN** 标识符输入框启用，行为与 MANUAL 模式一致；保留单 SI 可改与命名空间校验

#### Scenario: 旧客户端不带 mode 创建 Item

- **WHEN** 客户端以不含 `identifierMode` / `versionMode` 字段的请求体创建 Item，且 identifier 有显式值
- **THEN** 后端写入 `identifierCreationMode='MANUAL'`、`versionCreationMode` 按 version 字段反推

### Requirement: 后端统一收口与向后兼容

所有 Item 子类的创建/更新 API MUST 接受可选的 `registration` 负载（`ItemRegistrationRequest`），并在存在时统一经由 `ItemAdministrationService.ensureAdministeredItem` 处理；未携带 `registration` 的旧调用方 MUST 继续按原行为工作，不因此报错。

#### Scenario: 旧客户端不带 registration 创建许可值-值含义关联
- **WHEN** 客户端以不含 `registration` 字段的请求体调用 PermissibleValueMeaning 创建接口
- **THEN** 创建成功，行为与变更前一致

#### Scenario: 带 registration 创建许可值-值含义关联
- **WHEN** 客户端携带 `registration{namespaceId, identifier, version}` 调用 PermissibleValueMeaning 创建接口
- **THEN** 创建成功且生成对应的 ScopedIdentifier，策略校验与数据元路径一致

### Requirement: Item 创建选择（AUTO/MANUAL）元数据可回溯

系统 MUST 将 Item 创建时用户选择的标识符 / 版本生成方式（`AUTO` 或 `MANUAL`）持久化到 `ScopedIdentifier.identifierCreationMode` 与 `versionCreationMode`。这两字段 MUST 由 `ScopedIdentifierResponse` 与 `ItemMetaResponse` 暴露在 API 响应中，便于任意时刻通过 API 检索"该 Item 创建时是自动生成还是手动录入"这一元数据。字段为 NULL 时表示迁移前既存记录或外部命名空间下的 SI（按 MANUAL 路径处理）。具体生成策略类型（UUID / URN / SEQUENCE / TEMPLATE / SEMVER / SEQUENTIAL / DATE）由命名空间级配置统一承载，不在本字段中重复记录。

#### Scenario: 创建 AUTO 后响应包含 AUTO

- **WHEN** 用户在自有命名空间下创建 Item 并选择标识符自动生成
- **THEN** 响应 `ScopedIdentifierResponse.identifierCreationMode='AUTO'`，调用方后续可通过 API 直接读到该 Item 由 AUTO 方式生成

#### Scenario: 创建 MANUAL 后响应包含 MANUAL

- **WHEN** 用户手动输入标识符与版本创建 Item
- **THEN** 响应 `ScopedIdentifierResponse.identifierCreationMode='MANUAL'` 与 `versionCreationMode='MANUAL'`，标识该字段为手动录入

#### Scenario: 通过 API 检索既存 Item 的创建选择

- **WHEN** 业务系统查询一个 Item 的 `ItemMetaResponse`
- **THEN** 响应 MUST 包含 `identifierCreationMode` 与 `versionCreationMode`，调用方可据此识别该 Item 是按 AUTO 还是 MANUAL 创建的

#### Scenario: 联邦合并后的双标识条目各自保留 mode 字段

- **WHEN** 联邦合并产生双标识条目（本地 + 来源）
- **THEN** 每条 ScopedIdentifier 独立保留自己的 `identifierCreationMode` / `versionCreationMode`，互不影响
