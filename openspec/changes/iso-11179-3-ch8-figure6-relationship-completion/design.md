## Context

ISO/IEC 11179-3:2023 第 8 章 Figure 6 的核心关系已在后端落地为 `Designation`、`Definition`、`Context`、`DesignationContext`、`DefinitionContext`、`DesignationDefinitionPairing`、`Acceptability` 等实体，并在 `metadata-registry-web` 中提供了 `ItemRelationshipController` 聚合端点。`iso-11179-3-ch8-naming-definition-gap-closure` 变更已引入 `NamingConvention` 实体，但将 `Namespace` 与 `NamingConvention` 建模为单向外键，与标准中的 many-to-many `naming_convention_utilization` 关系不符，且未实现 `Namespace` 约束指示器的校验。`iso-11179-3-ch8-frontend-interaction-alignment` 变更已规划前端关系增强，但尚未完成实现，且未覆盖命名空间/命名约定关系一致性、直接可接受性徽章等细节。

本变更在上述两个变更的基础上，完成 Figure 6 中尚未对齐的最后一英里：

- 后端关系与标准属性补全；
- 前端将复杂关系以可查看、可编辑、可过滤的方式呈现。

## Goals / Non-Goals

**Goals:**
- 让 `Namespace`、`NamingConvention`、`Designation`、`Definition`、`Context`、`DesignationDefinitionPairing` 之间的关联与 ISO/IEC 11179-3:2023 8.1/8.3/8.4/8.5/8.6 描述一致。
- 在 `MetadataDetailView` 中以只读关系表格完整展示指称、定义、上下文、配对四类关系。
- 让上下文从「下拉选项」提升为可反向浏览的独立关系节点。
- 让 `Acceptability` 在列表、详情、编辑表单中可见，而不是完全折叠在上下文面板内。
- 优化配对创建流程，减少保存后二次进入弹窗的割裂感。
- 为命名空间增加命名约定多选器及约束校验提示。

**Non-Goals:**
- 不重新设计整个元数据编辑工作流；保留现有标签页弹窗模式。
- 不引入完整的图形化元模型编辑器（如 UML 图）；只增加关系卡片/列表视图。
- 不改动 `Classification`（第 10 章）和 `Mapping`（第 11 章）等已规划但尚未实现的其他区域。
- 不修改 `NamingConventionEngine` 的规则执行语义；仅扩展其读取标准属性的能力。

## Decisions

1. **Namespace ↔ NamingConvention 改为 many-to-many**
   - 理由：标准 8.6.6 定义 `naming_convention_utilization` 为 Namespace 与 NamingConvention 之间的 many-to-many 关联；当前 `ManyToOne` 只能表达一个命名约定，限制过强。
   - 实现：新增 `namespace_naming_convention` 关联表，保留 `namespace.naming_convention_id` 列作为读取时的首选过渡值，但持久化通过关联表写入；`NamespaceRequest`/`Response` 使用 `namingConventionIds` 列表。

2. **NamingConvention 标准属性与 rulesJson 并存**
   - 理由：标准 8.4.4 要求 `scope_rule`、`authority_rule`、`semantic_rule`、`syntactic_rule`、`lexical_rule` 五个属性；已有 `rulesJson` 用于机器执行，不能丢弃。
   - 实现：在 `NamingConvention` 实体新增标准字段，API 同时暴露 `rulesJson` 和规则字段；规则引擎优先读取 `rulesJson`，当其为空时按标准字段解析成可执行规则。

3. **Namespace 指示器约束后端统一校验**
   - 理由：标准 8.3.1.5 对 `mandatory_naming_convention_indicator`、`one_name_per_item_indicator`、`one_item_per_name_indicator` 有明确约束，放在 `NamespaceService` 中统一校验最可靠。
   - 实现：新增 `NamespaceConstraintValidator`，在 `NamespaceService.create/update` 及 `ItemAdministrationService.applyDesignations`（当命名空间强制命名约定时）调用；校验失败抛出 `ConstraintViolationException` 并返回 400。

4. **Designation/Definition 直接可接受性可选**
   - 理由：标准 8.7.1 提到 `Designation.acceptability` 和 `Definition.acceptability`，但 Figure 6 及 8.5 主要将可接受性放在关联类上；为避免数据重复，新增字段但允许为空，由 UI 默认取上下文/配对中的可接受性。
   - 实现：在 `Designation` 与 `Definition` 上增加 `acceptability` 枚举列；`TextLineResponse` 增加 `acceptability` 字段；编辑器默认不显示，详情页优先展示。

5. **前端关系数据使用独立聚合端点**
   - 理由：已有的 `ItemRelationshipController.getRelationships` 已避免 `AdministeredItemResponse` 膨胀；本变更扩展其返回上下文关系与命名约定引用。
   - 实现：扩展 `ItemRelationshipResponse` 增加 `contexts`、`namingConventions` 字段；前端 `MetadataDetailView` 懒加载各标签页。

6. **配对编辑支持「保存后配对」与「临时 ID 配对」两阶段**
   - 理由：在同一弹窗内完成配对需要临时 ID 映射，实现复杂度中等；先保证保存后配对可用，再提供临时 ID 作为可选增强。
   - 实现：第一阶段完善 `DesignationDefinitionPairingEditor` 的重复校验和可读标签；第二阶段在 `ItemFormDialog` 中为未保存行分配 `__tmp_id__`，保存时由后端 `ItemAdministrationService` 替换为真实 ID 并同步配对。

7. **可接受性徽章颜色语义**
   - `PREFERRED`：success；`ADMITTED`：info；`DEPRECATED`、`SUPERSEDED`、`OBSOLETE`：danger。
   - 列表中仅对首条 PREFERRED 指称显示徽章，避免杂乱；详情关系表中每行显示。

8. **上下文反向浏览弹窗分页**
   - 理由：某个上下文可能关联大量指称/定义，避免弹窗数据爆炸。
   - 实现：`ContextService.findUsages(contextId)` 返回分页结果，弹窗使用 `el-table` + 分页，上限提示。

## Risks / Trade-offs

- [风险] `Namespace` 与 `NamingConvention` 改为 many-to-many 后，现有数据需要迁移；过渡列可减少迁移风险，但会带来双重来源。
  - 缓解：新增 Flyway 迁移脚本将单列外键写入关联表，并保持原列只读；迁移后逐步废弃原列。
- [风险] 临时 ID 配对流程在前后端映射复杂，容易出错。
  - 缓解：作为可选第二阶段，先实现保存后配对；临时 ID 实现需覆盖完整单元测试和 E2E 测试。
- [风险] 详情页新增多个标签页增加 API 调用次数。
  - 缓解：使用按需懒加载，并缓存已加载数据；聚合端点一次性返回所有关系数据，前端按标签页拆分展示。
- [风险] 可接受性标签过多导致 UI 杂乱。
  - 缓解：仅在详情关系表和首条首选徽章中展示；列表单元格不堆叠标签。
- [风险] 约束校验引入新异常，可能破坏现有集成测试。
  - 缓解：在现有 `NamespaceControllerTest` 中增加默认 `oneItemPerNameIndicator=false` 的测试数据，并为新增约束提供独立测试。

## Migration Plan

1. 后端新增 `namespace_naming_convention` 关联表 Flyway 迁移；将 `namespace.naming_convention_id` 非空值复制到关联表。
2. 后端 `naming_convention` 表新增 `scope_rule`、`authority_rule`、`semantic_rule`、`syntactic_rule`、`lexical_rule` 列；`designation`/`definition` 表新增 `acceptability` 列。
3. 部署顺序：先运行后端迁移和启动，再更新前端；回滚时删除新增列和关联表，并恢复单列外键逻辑。
4. 测试顺序：先运行 `mvn test` 验证后端迁移和约束；再运行 `npm run build` 验证前端；最后 `openspec validate --all` 验证变更文档。

## Open Questions

- `NamingConvention` 标准属性与 `rulesJson` 的优先级如何定义？建议：`rulesJson` 不为空时优先使用，否则从标准字段构建规则。
- 临时 ID 配对流程是否在本次变更中实现，还是仅作为后续增强？建议分为两阶段，第一阶段确保基础配对可用。
- 上下文反向浏览是否需要在独立的 `ContextDetailView` 页面中实现，还是弹窗即可？建议先弹窗，后续如需常驻再新增页面。
