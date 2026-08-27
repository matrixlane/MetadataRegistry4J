## MODIFIED Requirements

### Requirement: 所有 Item 子类界面提供统一的标识符/版本编辑区段

系统 SHALL 为所有具有 identified_item 的 Item 子类（包括但不限于 DataElement、DataElementConcept、ObjectClass、Property、ValueMeaning、MeasureClass、UnitOfMeasure、Dimensionality、DataType、DataTypeScheme、ConceptualDomain、ValueDomain、Context、ConstraintSet、NamingConvention、RegistrySpecification）的创建/编辑界面提供与数据元一致的注册区段：命名空间选择、标识符输入（含内嵌的自动/手工切换控件与段模板变量录入）、版本输入（含内嵌的自动/手工切换控件）。"标识符生成方式"与"版本生成方式" SHALL 作为对应输入框的内嵌 UI 元素存在（与输入框视觉复合，承载于 `el-input` 的 `append` 槽位），不得再以独立的表单字段行呈现。该区段 MUST 由同一可复用组件实现，禁止各页面各自维护一份发散的标识符编辑逻辑。ClassificationScheme / ClassificationNode / MappingScheme 不是 Item 子类，其界面不受本要求约束。

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
