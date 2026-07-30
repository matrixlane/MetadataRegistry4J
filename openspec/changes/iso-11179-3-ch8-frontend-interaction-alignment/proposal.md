## Why

ISO/IEC 11179-3:2023 第 8.1 节 Figure 6 将 `Designation`、`Definition`、`Context`、`DesignationContext`、`DefinitionContext`、`DesignationDefinitionPairing` 之间的关系建模得较为复杂：一个 `Item` 拥有多条指称/定义，每条指称/定义可关联多个 `Context` 并携带 `Acceptability`，同时指称与定义之间可建立配对。当前后端模型（`Designation`、`Definition`、`DesignationContext`、`DefinitionContext`、`DesignationDefinitionPairing`）已覆盖这些实体，但前端交互仅做了最简表达：

- 指称/定义编辑器缺少与 `ReferenceDocument`（来源文献）的关联输入；
- 元数据详情页完全不展示指称、定义、上下文、配对关系；
- 配对编辑只能在保存后使用，且与指称/定义创建流程割裂；
- 上下文仅作为下拉选项出现，没有反向浏览（哪些指称/定义使用了某上下文）；
- 可接受性（Acceptability）被折叠隐藏，列表和概览中不可见；
- 数据字典搜索未利用上下文、可接受性等关系维度。

因此需要一次前端交互层面的补充设计，使复杂元模型关系在 UI 中既准确又可操作。

## What Changes

- 在 `MultilingualTextEditor` 中为每条指称/定义增加 `ReferenceDocument`（来源文献）选择器，补齐 Figure 6 中 `Designation`/`Definition` → `ReferenceDocument` 的关系。
- 扩展元数据详情页（`MetadataDetailView`），新增「指称」「定义」「上下文」「配对」四个标签页，展示完整关系数据；同步扩展后端 `AdministeredItemResponse` 或新增独立端点以返回这些关系。
- 重构 `ItemFormDialog` 的配对编辑流程：允许在创建指称/定义时即生成临时 ID，支持在同一弹窗内完成配对创建，减少保存后二次编辑的割裂感。
- 新增上下文反向浏览视图：在 `ContextList` 或 `ContextDetail` 中展示引用该上下文的所有指称/定义及配对。
- 在列表、详情、搜索结果中显式展示 `Acceptability`（可接受性）标签或徽章。
- 扩展数据字典搜索：支持按上下文、语言、可接受性过滤，并在结果卡片中展示匹配上下文。
- 为 `DesignationDefinitionPairingEditor` 增加配对校验（避免重复配对、强制 context+acceptability 一致性提示）。
- 补充前端单元测试与 E2E 测试，覆盖指称/定义/上下文/配对的核心交互路径。

## Capabilities

### New Capabilities

- `iso-11179-3-ch8-frontend-reference-source`: 在前端指称/定义编辑器中支持 `ReferenceDocument` 来源选择。
- `iso-11179-3-ch8-frontend-detail-relationships`: 在元数据详情页展示指称、定义、上下文、配对及其关系。
- `iso-11179-3-ch8-frontend-context-browsing`: 支持从上下文反向浏览关联的指称/定义和配对。
- `iso-11179-3-ch8-frontend-pairing-flow`: 优化指称-定义配对创建流程，支持在同弹窗内完成。
- `iso-11179-3-ch8-frontend-search-by-context`: 数据字典支持按上下文与可接受性过滤。

### Modified Capabilities

- （无现有 OpenSpec 规范需要修改；本次为前端交互与展示增强。）

## Impact

- `metadata-registry-ui`：修改 `MultilingualTextEditor.vue`、`ItemFormDialog.vue`、`DesignationDefinitionPairingEditor.vue`、`MetadataDetailView.vue`、`BrowserView.vue`、`ContextList.vue`；可能新增 `ContextDetailView.vue` 或 `RelationshipGraph.vue`。
- `metadata-registry-web`：扩展 `AdministeredItemResponse` 或新增 `/items/{id}/designation-definition-relationships` 端点；扩展 `ContextService` 提供反向查询。
- 数据库与后端服务：可能需新增查询方法以支持反向上下文浏览和详情关系加载。
- 测试：`metadata-registry-ui` 新增组件测试与 E2E 测试；`metadata-registry-web` 补充集成测试。
