## MODIFIED Requirements

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

### Requirement: 所有 Item 子类界面提供统一的标识符/版本编辑区段

系统 SHALL 为所有具有 identified_item 的 Item 子类（包括但不限于 DataElement、DataElementConcept、ObjectClass、Property、ValueMeaning、MeasureClass、UnitOfMeasure、Dimensionality、DataType、DataTypeScheme、ConceptualDomain、ValueDomain、Context、ConstraintSet、NamingConvention、RegistrySpecification）的创建/编辑界面提供与数据元一致的注册区段：命名空间选择、标识符输入（含内嵌的自动/手工切换控件与段模板变量录入）、版本输入（含内嵌的自动/手工切换控件）。"标识符生成方式"与"版本生成方式" SHALL 作为对应输入框的内嵌 UI 元素存在（与输入框视觉复合，承载于 `el-input` 的 `append` 槽位），不得再以独立的表单字段行呈现。该区段 MUST 由同一可复用组件实现，禁止各页面各自维护一份发散的标识符编辑逻辑。编辑态 MUST 按持久化的 `identifierCreationMode` / `versionCreationMode` 决定输入控件与切换控件的状态（AUTO 锁定 / MANUAL 开放 / NULL 等价 MANUAL）。系统 MUST 在响应中通过 `ItemMetaResponse` 暴露这两个字段供编辑态透传与将来 API 检索。ClassificationScheme / ClassificationNode / MappingScheme 不是 Item 子类，其界面不受本要求约束。

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

## ADDED Requirements

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
