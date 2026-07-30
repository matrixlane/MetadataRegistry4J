## Context

前期变更 `iso-11179-3-ch8-figure6-relationship-completion` 已完成后端主体：

- `Namespace` 与 `NamingConvention` 改为 many-to-many（`namespace_naming_convention` 关联表）。
- `NamingConvention` 补充 `scopeRule`、`authorityRule`、`semanticRule`、`syntacticRule`、`lexicalRule`。
- `Designation`/`Definition` 增加直接 `acceptability` 字段与关联上下文可接受性。
- `ItemRelationshipController` 新增 `/api/v1/items/{itemId}/designation-definition-relationships` 聚合端点，返回指称、定义、配对、上下文、命名约定。

验证过程中发现：

1. `NamespaceConstraintValidator` 仅校验 `mandatory_naming_convention_indicator` 的「恰好一个命名约定」条件，未实现 `one_name_per_item_indicator`、`one_item_per_name_indicator` 以及强制命名约定时指称与命名约定的一致性。
2. 由于 `TextLine` record 增加 `acceptability` 与 `contexts` 字段后，部分测试与种子数据构造器未同步更新，导致编译失败；已在本轮检查中先行修复。
3. 前端 `MultilingualTextEditor` 未暴露 `sourceId`（来源文献）选择器，Figure 6 中 `Designation`/`Definition` → `ReferenceDocument` 关系不可编辑。
4. `MetadataDetailView` 仍只展示核心属性与注册信息，未展示指称、定义、上下文、配对关系。
5. `ContextList` 仅作为普通 `Item` 管理，无反向引用浏览。
6. `BrowserView` 搜索仅支持关键词与类型/状态，无法按上下文或可接受性过滤。
7. `DesignationDefinitionPairingEditor` 在新建项中不可用（依赖后端真实 ID），且缺少重复校验和选项可读标签。
8. `NamespaceList` 表单仍是单标识符/名称字段，未暴露多命名约定和三个指示器开关。

## Goals / Non-Goals

**Goals:**

- 让 Figure 6 中所有与前端用户可见的关系（来源、上下文、配对、命名约定、可接受性）至少可被查看，大部分可被编辑。
- 在详情页提供关系视图，避免把复杂关系完全隐藏在编辑弹窗内。
- 让上下文从「下拉选项」提升为可反向浏览的独立关系节点。
- 让可接受性在列表和详情中可见，而非完全折叠。
- 优化配对创建流程，减少保存后二次进入弹窗的割裂感。
- 完成后端 `Namespace` 约束校验，确保与标准一致。

**Non-Goals：**

- 不重新设计整个元数据编辑工作流；保留现有标签页弹窗模式。
- 不引入完整的图形化元模型编辑器（如 UML 图）；只增加关系卡片/列表视图。
- 不修改后端的 `Designation`/`Definition`/`Context` 域模型结构，只扩展 DTO/查询与校验逻辑。
- 不新增数据库迁移，复用前期变更脚本。

## Decisions

1. **来源文献选择器**：在 `MultilingualTextEditor` 的每一行中增加一个 `ReferenceDocument` 下拉选择（使用已有的 `referenceDocumentApi`）。该字段可选，不阻塞保存，以符合标准中「来源是可选关联」的语义。选择器放在语言/值行下方，仅在展开「上下文关联」时一并显示，避免基础行拥挤。

2. **详情页关系展示**：在 `MetadataDetailView` 中新增 `el-tabs` 标签页：「概览」「指称」「定义」「上下文」「配对」「版本历史」。每个关系标签页使用 `el-descriptions` 或表格展示关系数据。后端通过现有 `/api/v1/items/{itemId}/designation-definition-relationships` 端点统一返回反规范化的关系视图；该端点已存在，只需补全配对上下文解析。

3. **配对创建流程**：在 `ItemFormDialog` 中，为未保存的指称/定义行分配临时客户端 ID（`__tmp_id__`），配对编辑使用临时 ID 进行本地关联；保存时由后端将临时 ID 替换为真实 ID。这允许在新建弹窗中直接完成配对，但实现复杂度中等，因此作为本次设计中的可选高级任务（Tasks 中标记为可选）。基础方案仍保留「保存后编辑配对」流程。

4. **上下文反向浏览**：在 `ContextList` 中增加「查看引用」操作，弹窗展示引用该上下文的所有 `DesignationContext`、`DefinitionContext` 和 `DesignationDefinitionPairing`。后端在 `ContextService` 中新增 `findUsages(UUID contextId)` 查询；前端新建 `ContextUsageDialog.vue` 组件。

5. **可接受性可视化**：在列表中，为首条首选（PREFERRED）指称增加徽章；在详情关系表格中，每行展示 `Acceptability` 标签。颜色语义：PREFERRED=success，ADMITTED=info，DEPRECATED/OBSOLETE/SUPERSEDED=danger。列表中仅在首条首选处显示，避免堆叠。

6. **搜索增强**：在 `BrowserView` 增加「上下文过滤」和「可接受性过滤」两个折叠高级筛选器。后端本次同步扩展 `/metadata/search` 支持 `contextId` 与 `acceptability` 参数；前端仍保留参数未生效时的非阻塞提示作为健壮性兜底（与现有 `metadataSearchApi` 扩展同步）。

7. **组件职责划分**：`MultilingualTextEditor` 继续负责单条指称/定义及其上下文与来源；`DesignationDefinitionPairingEditor` 负责配对；`MetadataDetailView` 负责只读关系展示；`ContextList` 负责上下文管理及反向浏览入口；`BrowserView` 负责搜索与结果展示。避免一个组件承担过多职责。

8. **后端校验策略**：`NamespaceConstraintValidator` 在 `NamespaceService.create/update` 以及 `ItemAdministrationService.applyDesignations`（当目标项所在命名空间强制命名约定时）被调用。`one_name_per_item_indicator` 和 `one_item_per_name_indicator` 通过数据库查询实现；强制命名约定时指称一致性通过比对指称关联的 `NamingConvention` 与命名空间关联的 `NamingConvention` 实现。

## Risks / Trade-offs

- [风险] 新增详情端点可能导致 N+1 查询。缓解措施：后端使用 JOIN FETCH 或一次性查询返回关系树；在任务中明确增加性能测试。
- [风险] 临时 ID 配对流程在前后端映射复杂，容易出错。缓解措施：作为可选任务，先实现「保存后编辑配对」的基础流程，再评估是否实现临时 ID。
- [风险] 上下文反向浏览可能返回大量数据。缓解措施：分页展示，限制最多 100 条，超出时提示用户细化搜索。
- [风险] 可接受性标签过多导致 UI 杂乱。缓解措施：仅在详情关系表和首条首选徽章中展示，避免列表每个单元格堆叠标签。
- [风险] 后端 `Namespace` 约束校验引入数据库查询，保存性能下降。缓解措施：校验仅在创建/更新时执行，且使用索引字段查询；必要时可缓存命名空间下名称映射。
- [权衡] 详情页关系标签页增加 API 调用次数。缓解措施：使用按需加载（懒加载 tab 内容），并缓存已加载数据。

## Migration Plan

- 后端：扩展 `NamespaceConstraintValidator` 方法，新增 `ContextService.findUsages` 方法及 `/api/v1/contexts/{id}/usages` 端点；扩展 `/metadata/search` 支持 `contextId` 和 `acceptability`；补全 `ItemRelationshipController` 配对上下文解析。
- 前端：按依赖顺序修改组件：先 `MultilingualTextEditor`（来源选择），再 `MetadataDetailView`（关系标签页），再 `ContextList`（反向浏览），最后 `BrowserView`（搜索增强）与 `NamespaceList`（治理属性）。
- 部署：无需数据迁移，但需验证现有数据的指称/定义/上下文/配对在新 UI 中正确展示。
- 回滚：前端回滚只需恢复 Vue 文件；后端回滚只需恢复 validator 与 controller 代码；数据库无新结构，无需回滚。

## Open Questions

（以下问题均已在设计中裁定，保留记录以备追溯。）

- 临时 ID 配对流程是否在本次实现，还是仅作为后续增强？**已裁定**：作为本次可选高级任务（见 Decisions 3 与 Tasks 7.3），基础流程保留「保存后编辑配对」。
- 上下文反向浏览是否需要在独立的 `ContextDetailView` 页面中实现，还是弹窗即可？**已裁定**：弹窗即可（`ContextUsageDialog.vue`，见 Decisions 4），不新增独立页面。
- 搜索过滤中的「上下文」是否支持多选？**已裁定**：单选先行（Tasks 6.3），多选作为后续增强。
