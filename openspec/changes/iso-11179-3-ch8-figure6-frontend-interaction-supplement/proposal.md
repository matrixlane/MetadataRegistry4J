## Why

ISO/IEC 11179-3:2023 第 8 章 Figure 6 定义了 `Item`、`Designation`、`Definition`、`Context`、`Designation_Context`、`Definition_Context`、`Designation_Definition_Pairing`、`Naming_Convention` 和 `Namespace` 之间的一组复杂关系。前期变更（`iso-11179-3-ch8-figure6-relationship-completion`）已完成后端主体实现，但前端页面尚未按这些关系进行系统表达：关键关系（指称↔来源文献、指称↔上下文、定义↔上下文、配对↔上下文、命名空间↔命名约定）在编辑和详情页中或被折叠、或不可见、或无法反向浏览。同时，后端 `Namespace` 的三项约束指示器仅实现了 `mandatory_naming_convention_indicator`，`one_name_per_item_indicator` 和 `one_item_per_name_indicator` 尚未校验，且未强制同一命名空间下指称与命名约定的一致性。本补充计划将闭合这些 gaps，使 Figure 6 的关系在前端可交互、在后端可校验。

## What Changes

- 在 `MultilingualTextEditor.vue` 每行增加 `ReferenceDocument`（来源文献）选择器，表达 `Designation`/`Definition` → `ReferenceDocument` 关系；同时为每行直接可接受性评级提供可视化徽章。
- 在 `MetadataDetailView.vue` 增加「指称」「定义」「上下文」「配对」懒加载标签页，展示聚合端点返回的关系数据，并支持跳转到关联项。
- 在 `ContextList.vue` 增加「查看引用」操作，弹窗展示引用该上下文的所有 `Designation_Context`、`Definition_Context` 和 `Designation_Definition_Pairing`；后端新增 `/api/v1/contexts/{id}/usages` 端点提供反向引用数据。
- 扩展 `BrowserView.vue` 高级筛选：支持按上下文和可接受性过滤，并在结果行展示首选（PREFERRED）徽章；后端 `/metadata/search` 同步支持 `contextId` 与 `acceptability` 参数。
- 优化 `DesignationDefinitionPairingEditor.vue`：选项显示语言与上下文标签、重复配对（designationId + definitionId + contextId）实时校验、支持在新建弹窗内使用临时客户端 ID 创建配对（可选高级实现）。
- 更新 `NamespaceList.vue` 表单：增加命名约定多选器和三个约束指示器开关，并展示后端校验错误。
- 完成后端 `NamespaceConstraintValidator`：实现 `one_name_per_item_indicator`、`one_item_per_name_indicator` 以及强制命名约定时同一命名空间下指称必须遵循同一命名约定的校验。
- 补全 `ItemRelationshipController`：解析配对中的 `contextId` 为完整上下文标签，并补充集成测试。
- 更新 `ISO_11179-3_2023_Comparison_Report.md` 第 8 章状态，标记剩余未对齐项。

## Capabilities

### New Capabilities

- `frontend-source-reference-selector`: 在前端指称/定义编辑器中支持 `ReferenceDocument` 来源选择，补齐 Figure 6 中 `Designation`/`Definition` → `ReferenceDocument` 关系。
- `frontend-detail-relationship-tabs`: 在元数据详情页展示指称、定义、上下文、配对及其关系。
- `frontend-context-reverse-browsing`: 支持从上下文反向浏览关联的指称、定义和配对。
- `frontend-acceptability-visualization`: 在列表与详情中显式展示 `Acceptability` 可接受性徽章，并支持按可接受性筛选。
- `frontend-pairing-flow-improvement`: 优化指称-定义配对创建流程，支持在同一弹窗内完成并校验重复。
- `frontend-namespace-governance`: 命名空间表单支持多命名约定关联和三个约束指示器开关。
- `backend-namespace-constraints-completion`: 完成后端命名空间三项约束指示器及指称-命名约定一致性校验。
- `backend-relationship-endpoint-completion`: 补全聚合关系端点中配对上下文解析与集成测试。
- `backend-context-usages-endpoint`: 后端提供 `/api/v1/contexts/{id}/usages` 反向引用查询端点，支撑上下文反向浏览。
- `backend-search-relation-filters`: 后端 `/metadata/search` 支持 `contextId` 与 `acceptability` 关系维度过滤。
- `documentation-11179-ch8-frontend-supplement`: 更新 `ISO_11179-3_2023_Comparison_Report.md` 第 8 章状态以反映本补充的对齐结果。

### Modified Capabilities

（无现有 OpenSpec 规范的需求行为需要修改；本补充计划仅针对前期变更未覆盖的前端表达与后端校验缺口。）

## Impact

- `metadata-registry-ui`: 修改 `MultilingualTextEditor.vue`、`DesignationDefinitionPairingEditor.vue`、`ItemFormDialog.vue`、`MetadataDetailView.vue`、`ContextList.vue`、`BrowserView.vue`、`NamespaceList.vue`，新增 `ContextUsageDialog.vue`，扩展类型定义与 API 资源。
- `metadata-registry-web`: 扩展 `NamespaceConstraintValidator`、补充 `NamespaceService` 与 `ItemAdministrationService` 校验调用；完善 `ItemRelationshipController` 上下文解析；新增 `ContextService.findUsages` 与 `/api/v1/contexts/{id}/usages` 端点；扩展 `/metadata/search` 支持 `contextId`/`acceptability` 过滤；新增/更新集成测试。
- `metadata-registry-common-facilities`: 无需修改实体，沿用前期变更的 `Designation`/`Definition`/`Namespace`/`NamingConvention` 结构。
- 数据库：无新迁移，复用前期 Flyway 脚本；仅需确保现有数据在新 UI 中正确展示。
- 文档：更新 `ISO_11179-3_2023_Comparison_Report.md` 第 8 章状态。
