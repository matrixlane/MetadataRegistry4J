## Context

当前后端已按 ISO/IEC 11179-3:2023 第 8 章实现 `Designation`、`Definition`、`Context`、`DesignationContext`、`DefinitionContext`、`DesignationDefinitionPairing`，并通过 `TextLine`/`TextLineResponse` 与 `DesignationDefinitionPairingRequest`/`Response` 暴露给前端。前端现有组件：

- `MultilingualTextEditor.vue`：用「语言 + 值」表达指称/定义，并以内嵌折叠面板支持上下文与可接受性。
- `DesignationDefinitionPairingEditor.vue`：用两个下拉框选择指称与定义，再选一个上下文和可接受性。
- `ItemFormDialog.vue`：以标签页聚合注册信息、指称、定义、配对、资源专属字段；配对页仅在 `form.id` 存在时出现。
- `MetadataDetailView.vue`：仅展示 `AdministeredItemResponse` 返回的摘要（标识符、版本、状态、注册机构等），不展示任何指称/定义/上下文/配对。
- `BrowserView.vue`：展示数据字典列表，搜索框仅支持关键词，未利用上下文或可接受性。
- `ContextList.vue`：将上下文作为普通 `Item` 管理，无反向关系浏览。

这些组件实现了基本 CRUD，但 Figure 6 中的多条关系（`Designation`/`Definition` 与 `ReferenceDocument`、指称-定义配对、上下文反向关联、可接受性可视化）在前端表达不完整或被折叠隐藏。

## Goals / Non-Goals

**Goals:**
- 让 Figure 6 中的关键关系在前端至少可被查看、大部分可被编辑。
- 在详情页提供完整的关系视图，而不是把复杂关系隐藏在编辑弹窗内。
- 让上下文从「下拉选项」提升为可反向浏览的独立关系节点。
- 让可接受性在列表和详情中可见，而非完全折叠。
- 优化配对创建流程，减少保存后二次进入弹窗的割裂感。

**Non-Goals:**
- 不重新设计整个元数据编辑工作流；保留现有标签页弹窗模式。
- 不引入完整的图形化元模型编辑器（如 UML 图）；只增加关系卡片/列表视图。
- 不修改后端的 `Designation`/`Definition`/`Context` 域模型结构，只扩展 DTO/查询。
- 不处理 `Naming_Convention`（已在 `iso-11179-3-ch8-naming-definition-gap-closure` 变更中覆盖）。

## Decisions

1. 来源文献选择器：在 `MultilingualTextEditor` 的每一行中增加一个 `ReferenceDocument` 下拉选择（使用已有的 `referenceDocumentApi`）。该字段可选，不阻塞保存，以符合标准中「来源是可选关联」的语义。
2. 详情页关系展示：在 `MetadataDetailView` 中新增 `el-tabs` 标签页：「概览」「指称」「定义」「上下文」「配对」「版本历史」。每个关系标签页使用 `el-descriptions` 或表格展示关系数据。后端通过新增 `/api/v1/items/{itemId}/designation-definition-relationships` 端点统一返回设计好（denormalized）的关系视图，避免直接扩展 `AdministeredItemResponse` 导致摘要接口臃肿。
3. 配对创建流程：在 `ItemFormDialog` 中，为未保存的指称/定义行分配临时客户端 ID（`__tmp_id__`），配对编辑使用临时 ID 进行本地关联；保存时由后端将临时 ID 替换为真实 ID。这允许在新建弹窗中直接完成配对，但实现复杂度中等，因此作为本次设计中的可选高级任务（Tasks 中标记为可选）。
4. 上下文反向浏览：在 `ContextList` 中增加「查看引用」操作，弹窗展示引用该上下文的所有 `DesignationContext`、`DefinitionContext` 和 `DesignationDefinitionPairing`。后端在 `ContextService` 中新增 `findUsages(UUID contextId)` 查询。
5. 可接受性可视化：在列表中，为首条首选（PREFERRED）指称增加徽章；在详情关系表格中，每行展示 `Acceptability` 标签。颜色语义：PREFERRED=success，ADMITTED=info，DEPRECATED/OBSOLETE/SUPERSEDED=danger。
6. 搜索增强：在 `BrowserView` 增加「上下文过滤」和「可接受性过滤」两个折叠高级筛选器。后端 `/metadata/search` 需支持这些参数；若后端暂不支持，本次前端先预留 UI 并在参数未生效时给出提示。
7. 组件职责划分：`MultilingualTextEditor` 继续负责单条指称/定义及其上下文；`DesignationDefinitionPairingEditor` 负责配对；`MetadataDetailView` 负责只读关系展示；`ContextList` 负责上下文管理及反向浏览入口。避免一个组件承担过多职责。

## Risks / Trade-offs

- [风险] 新增详情端点可能导致 N+1 查询。缓解措施：后端使用 JOIN FETCH 或一次性查询返回关系树；在任务中明确增加性能测试。
- [风险] 临时 ID 配对流程在前后端映射复杂，容易出错。缓解措施：作为可选任务，先实现「保存后编辑配对」的基础流程，再评估是否实现临时 ID。
- [风险] 上下文反向浏览可能返回大量数据。缓解措施：分页展示，限制最多 100 条，超出时提示用户细化搜索。
- [风险] 可接受性标签过多导致 UI 杂乱。缓解措施：仅在详情关系表和首条首选徽章中展示，避免列表每个单元格堆叠标签。
- [权衡] 详情页关系标签页增加 API 调用次数。缓解措施：使用按需加载（懒加载 tab 内容），并缓存已加载数据。

## Migration Plan

- 后端新增 `/api/v1/items/{itemId}/designation-definition-relationships` 聚合端点（或扩展 `AdministeredItemResponse`）。
- 后端 `ContextService` 新增 `findUsages` 方法及查询。
- 前端按依赖顺序修改组件：先 `MultilingualTextEditor`（来源选择），再 `MetadataDetailView`（关系标签页），再 `ContextList`（反向浏览），最后 `BrowserView`（搜索增强）。
- 部署后无需数据迁移，但需验证现有数据的指称/定义/上下文/配对在新 UI 中正确展示。

## Open Questions

- 是否优先扩展 `AdministeredItemResponse`，还是新增独立端点？独立端点更干净，但会增加一次请求。
- 临时 ID 配对流程是否在本次实现，还是仅作为后续增强？
- 上下文反向浏览是否需要在独立的 `ContextDetailView` 页面中实现，还是弹窗即可？
- 搜索过滤中的「上下文」是否支持多选？
