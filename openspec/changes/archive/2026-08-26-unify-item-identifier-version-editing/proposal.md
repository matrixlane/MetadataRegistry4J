## Why

目前只有数据元（DataElement）的创建/编辑界面提供完整的标识符与版本编辑能力：命名空间选择、按命名空间输入策略（AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY）自动或手工输入标识符、段模板变量填写、版本自动生成或手工指定。其它同样承载 identified_item（`IdentifiedItem` + `ScopedIdentifier`）的注册项（数据元概念、值域、概念域、物项类、特性、值含义、计量单位、度量类别、数据类型、数据类型方案、允许值系列、分类方案等）在界面上只有一个简单"标识符"输入框，甚至完全没有版本入口，后端部分实体的 Request DTO 也不接受 `registration` 负载。同一注册系统内同类行为的割裂既不符合 ISO/IEC 11179-3:2023 第 7 章（Scoped Identifier）对 identified item 的统一刻画，也使命名空间标识符策略（namespace-identifier-policy）只在数据元上真正生效。

## What Changes

- 前端：将 `ItemFormDialog` 中数据元的"命名空间 + 标识符（自动/手工 + 段模板变量）+ 版本（自动/手工）"编辑区抽取为可复用区段 `ItemRegistrationSection`，`ItemFormDialog` 回迁复用；将仍使用 `ResourceFormDialog` 裸标识符输入框的 Item 界面统一迁移到 `ItemFormDialog`：DataElementConcept、ObjectClass、Property、ValueMeaning、MeasureClass、UnitOfMeasure、DataType、DataTypeScheme。ConceptualDomain、Context、ValueDomain 已在 `ItemFormDialog` 上，行为随回迁自动一致。
- 后端：为尚无 `registration` 负载的 Item 类 Request DTO（PermissibleValueMeaningRequest）增加可选 `ItemRegistrationRequest registration` 字段，并在对应 Service 的 create/update 路径统一经由 `ItemAdministrationService.ensureAdministeredItem` 处理，使命名空间输入策略与版本策略在所有实体上一致生效。
- 编辑语义保持一致：编辑态标识符默认禁用（不可变），版本按命名空间版本策略递增或尊重用户显式指定；创建态按命名空间 `identifierInputMode` / `versionInputMode` 决定自动或手工。
- 非 Item 派生的类不在本次范围内：Contact、Organization、Individual、Registrar、DocumentType、ReferenceDocument 等 `Entity` 直接子类；ClassificationScheme / ClassificationNode / MappingScheme 经核实并非 `Item` 子类（无 identified_item），维持现状。AdministeredItem 生命周期页（AdministeredItemList）无创建/编辑入口（仅状态迁移），不涉及。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `identification`: 标识符/版本编辑要求从"仅数据元"扩展为"所有具有 identified_item 的 Item"，并明确创建/编辑态下命名空间输入策略与版本策略的统一应用规则。

## Impact

- 前端：`metadata-registry-ui/src/components/ItemFormDialog.vue`（抽取/复用注册区段）、约 14 个 `views/**` 列表页的创建/编辑对话框、`ItemFormDialog.spec.ts` 及相关组件测试。
- 后端：`metadata-registry-web` 的若干 `dto/**Request.java`、`service/**Service.java`（统一走 `ItemAdministrationService.ensureAdministeredItem`），控制器签名不变（仅请求体新增可选字段，向后兼容）。
- 无 schema / 迁移脚本变更；`ScopedIdentifier` 与 `IdentifiedItem` 模型不变。
- 依据：ISO/IEC 11179-3:2023 第 6.4 条（Item / Identified_Item）与第 7 章（Scoped Identifier）；数据集注册侧界面（如有）参照 ISO/IEC 11179-31:2023 元模型对 identified item 的同样刻画。
