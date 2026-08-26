## ADDED Requirements

### Requirement: 所有 Item 子类界面提供统一的标识符/版本编辑区段

系统 SHALL 为所有具有 identified_item 的 Item 子类（包括但不限于 DataElement、DataElementConcept、ObjectClass、Property、ValueMeaning、MeasureClass、UnitOfMeasure、Dimensionality、DataType、DataTypeScheme、ConceptualDomain、ValueDomain、Context、ConstraintSet、NamingConvention、RegistrySpecification）的创建/编辑界面提供与数据元一致的注册区段：命名空间选择、标识符输入（含自动/手工单选与段模板变量录入）、版本输入（含自动/手工单选）。该区段 MUST 由同一可复用组件实现，禁止各页面各自维护一份发散的标识符编辑逻辑。ClassificationScheme / ClassificationNode / MappingScheme 不是 Item 子类，其界面不受本要求约束。

#### Scenario: 数据元概念创建界面呈现完整注册区段
- **WHEN** 用户打开数据元概念（DataElementConcept）的新建对话框
- **THEN** 界面呈现命名空间选择、标识符（自动/手工）与版本（自动/手工）控件，行为与数据元新建对话框一致

#### Scenario: 非 Item 实体不受影响
- **WHEN** 用户打开 Contact / Organization / Individual / Registrar / DocumentType / ReferenceDocument 的创建/编辑界面
- **THEN** 界面不呈现注册区段，维持现状

#### Scenario: 分类方案界面不属于本能力范围
- **WHEN** 用户打开分类方案或分类节点的创建/编辑对话框
- **THEN** 维持其专有表单（方案名称/节点编码等），不呈现注册区段

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

在所有 Item 子类的编辑界面中，标识符输入 MUST 禁用（标识符一经分配不可变）；版本字段 SHALL 可编辑：用户显式指定新版本时经命名空间版本策略校验后采用，未修改时保存 SHALL 按命名空间版本策略递增。首次为历史遗留无标识的 Item 编辑保存时 SHALL 按所选命名空间策略补录 ScopedIdentifier。

#### Scenario: 编辑物项类时标识符禁用
- **WHEN** 用户打开已有 ObjectClass 的编辑对话框
- **THEN** 标识符输入框禁用并回显当前值，版本输入框可编辑

#### Scenario: 历史无标识实体编辑时补录
- **WHEN** 用户编辑一个尚无 ScopedIdentifier 的 Item，选择命名空间并保存
- **THEN** 后端创建对应的 AdministeredItem 与 ScopedIdentifier，标识符与版本按命名空间策略生成或采用手工输入

### Requirement: 后端统一收口与向后兼容

所有 Item 子类的创建/更新 API MUST 接受可选的 `registration` 负载（`ItemRegistrationRequest`），并在存在时统一经由 `ItemAdministrationService.ensureAdministeredItem` 处理；未携带 `registration` 的旧调用方 MUST 继续按原行为工作，不因此报错。

#### Scenario: 旧客户端不带 registration 创建许可值-值含义关联
- **WHEN** 客户端以不含 `registration` 字段的请求体调用 PermissibleValueMeaning 创建接口
- **THEN** 创建成功，行为与变更前一致

#### Scenario: 带 registration 创建许可值-值含义关联
- **WHEN** 客户端携带 `registration{namespaceId, identifier, version}` 调用 PermissibleValueMeaning 创建接口
- **THEN** 创建成功且生成对应的 ScopedIdentifier，策略校验与数据元路径一致
