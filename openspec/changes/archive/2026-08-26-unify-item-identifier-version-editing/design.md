## Context

数据元的创建/编辑走 `ItemFormDialog.vue`，其注册区段已实现完整逻辑：命名空间下拉、`identifierInputMode` / `versionInputMode`（AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY）驱动的自动/手工单选、段模板变量录入、编辑态禁用。提交负载为 `ItemRegistrationRequest{namespaceId, identifier, version, designations, definitions, variables}`，后端由 `ItemAdministrationService.ensureAdministeredItem` 统一落 `AdministeredItem` + `ScopedIdentifier`，经 `IdentifierInputPolicyService.resolveIdentifier/resolveVersion` 应用命名空间策略。

现状盘点：

- 已用 `ItemFormDialog` 的界面：DataElementList、ValueDomainList、ConceptualDomainList、ContextList。
- 已支持 `registration` 的后端 Request：ConceptRequest（覆盖 DataElementConcept / ObjectClass / Property / ValueMeaning / UnitOfMeasure / MeasureClass / Dimensionality 等 Concept 家族）、DataTypeRequest、DataTypeSchemeRequest、ValueDomainRequest、ConceptualDomainRequest、ContextRequest、ConstraintSetRequest、NamingConventionRequest、RegistrySpecificationRequest、DataElementRequest。
- 缺口（前端 `ResourceFormDialog` 自绘对话框，仅一个 identifier 输入框、无版本/无策略感知、且无指称编辑）：DataElementConceptList、ObjectClassList、PropertyList、ValueMeaningList、MeasureClassList、UnitOfMeasureList、DataTypeList、DataTypeSchemeList。
- 缺口（后端 Request 无 `registration`）：PermissibleValueMeaningRequest（无独立界面，仅补 API 层一致性）。
- 非 Item 实体不纳入：Contact / Organization / Individual / Registrar / DocumentType / ReferenceDocument（`Entity` 直接子类），ClassificationScheme / ClassificationNode / MappingScheme（非 `Item` 派生）。AdministeredItemList 无创建/编辑入口，不涉及。

## Goals / Non-Goals

**Goals:**
- 所有 Item 子类的创建/编辑界面在标识符与版本编辑上行为一致，等效于数据元界面。
- 命名空间输入策略与版本策略在所有 Item 实体的创建/更新路径统一生效（后端唯一收口 `ItemAdministrationService.ensureAdministeredItem`）。
- 编辑态标识符不可变、版本按策略递增或尊重显式指定的语义在所有实体保持一致。

**Non-Goals:**
- 不改动 `ScopedIdentifier` / `IdentifiedItem` 数据模型，不新增迁移脚本。
- 不处理非 Item 实体（Contact、Organization 等）的界面。
- 不改动联邦合并、数据字典 BrowserView 的既有专用逻辑（BrowserView 已有自己的 identifier/version 输入，作为只读/浏览场景不在本次统一范围）。
- 不引入多命名空间标识的多条 ScopedIdentifier 编辑（仍维持"每实体一条主标识"的现有编辑模型）。

## Decisions

- **前端复用方式：抽取注册区段组件 `ItemRegistrationSection.vue` 供 `ItemFormDialog` 回迁；既有 `ResourceFormDialog` 裸标识符页面整体迁移到 `ItemFormDialog`**。理由：这些 Item 页面除标识符/版本外同样缺指称（designations）编辑——后端 `ConceptRequest`/`DataTypeRequest` 等均 `@NotEmpty designations`，旧对话框创建本就无法成功；迁移到 `ItemFormDialog` 同时补齐指称/定义/配对与注册区段，与数据元体验完全一致。实体特有字段放入 `#fields` 槽位。
  - 备选方案（仅在旧对话框内嵌注册区段）被否：指称必填仍阻塞创建，且与"与数据元界面保持一致"的目标不符。
- **后端收口：只为缺口的 Item 类 Request DTO 增补可选 `registration` 字段并在 Service create/update 调 `ensureAdministeredItem`**。请求体新增可选字段向后兼容；控制器与路由不变。
  - Concept 家族与 DataType 家族后端已就绪，仅需前端接入。
- **编辑态语义沿用现状**：`ensureAdministeredItem` 已实现"单标识时允许修正 identifier、版本按策略递增或尊重显式值"（registry-merge-strategy D4/D5），不新增第二套规则。

## Risks / Trade-offs

- [区段抽取改变 ItemFormDialog 内部结构，影响既有 spec 测试] → 抽取后先跑 `ItemFormDialog.spec.ts`，保持对外 props/事件签名不变。
- [Concept 家族共用一个后端入口（ConceptContentApplier），前端逐页接入时行为差异不易发现] → 在 tasks 中逐页核对，并为区段组件写独立组件测试覆盖三种输入模式。
- [ClassificationScheme/ClassificationNode/MappingScheme 经核实不是 Item 子类] → 移出范围，其界面与 DTO 维持现状，spec 中明确排除。
- [部分实体历史上以无 registration 方式创建，编辑时首次补录标识] → `ensureAdministeredItem` 已处理"无 ScopedIdentifier 则创建"的路径，编辑保存即补录，语义自然成立。

## Migration Plan

纯代码变更，无数据迁移。回滚：还原本变更涉及的前后端提交即可，已落库的 ScopedIdentifier 数据不受影响。

## Open Questions

（无。MappingScheme、ClassificationScheme/Node 均已确认为非 Item 实体，排除。）
