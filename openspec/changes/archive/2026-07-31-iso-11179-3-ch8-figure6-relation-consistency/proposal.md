## Why

ISO/IEC 11179-3:2023 第 8 章 Figure 6 定义了 `Item`、`Designation`、`Definition`、`Context`、`Designation_Definition_Pairing`、`Naming_Convention` 与 `Namespace` 之间的复杂关系。前期变更（`iso-11179-3-ch8-figure6-relationship-completion` 与 `iso-11179-3-ch8-figure6-frontend-interaction-supplement`）已经让后端元模型与大部分前端交互落地，但在前端对“关系语义”的忠实表达和一致性校验上仍存在缺口：

- 配对（pairing）的上下文在标准中是“恰好一个”（`context_for_pairing` 8.6.1），而前端仍将其标记为“可选”；
- `Designation`（8.4.1.4）和 `Definition`（8.4.2.4）必须至少参与一个上下文关联或一次配对，前端目前没有强制或提示；
- `Acceptability` 评级要求“同一集合中只能有一个 PREFERRED”（8.7.1.3），前端没有冲突检测；
- 指称/定义的 `designation_namespace`（8.6.2）与 `naming_convention_conformance`（8.6.5）关系在前端不可见、不可编辑；
- 关系表格没有把 `Designation_Context` / `Definition_Context` 关联类上的 `acceptability` 显式展示出来；
- 当前 `npm run build` 仍有大量既有 TypeScript 类型错误，导致新增关系组件无法被可靠验证。

本补充变更将以前端关系一致性为重点，补全上述缺口，使 Figure 6 的语义在前端交互中得到准确反映，并为后端提供必要的 DTO/校验支撑。

## What Changes

- **配对上下文必填**：在 `DesignationDefinitionPairingEditor.vue` 中要求每条配对必须选择上下文；后端 `DesignationDefinitionPairing` 的 `context_id` 同步设为 `NOT NULL` 并添加校验（如现有数据为空，则提供一次性迁移脚本）。
- **最小上下文/配对提示**：在 `MultilingualTextEditor.vue` 中为每条指称/定义行增加内联校验：若未关联任何上下文且未出现在任何配对中，则给出警告，并阻止表单提交（除非用户显式标记为“例外”）。
- **PREFERRED 可接受性唯一性校验**：在指称/定义编辑器和关系表格中检测同一项（item）同一上下文/语言下出现多个 PREFERRED 的情况，并高亮冲突。
- **指称/定义的命名空间与命名约定**：扩展 `TextLineResponse` / `TextLineForm`，增加 `namespaceIds` 与 `namingConventionIds`；在 `MultilingualTextEditor.vue` 中增加可折叠的“命名空间与命名约定”区域；在 `MetadataDetailView.vue` 关系表格中展示这些关系。
- **关联类可接受性可视化**：在 `RelationshipTable.vue` 的上下文标签中展示 `Designation_Context` / `Definition_Context` 关联类上的 `acceptability`（例如 `上下文A · 首选`）。
- **上下文引用弹窗增强**：在 `ContextUsageDialog.vue` 中按所属项分组，区分 `Designation_Context`、`Definition_Context` 与 `Designation_Definition_Pairing`，并显示关联上的可接受性。
- **修复既有 TypeScript 构建错误**：批量修复 `*List.vue` 中 `prop` 类型、`AppLayout.vue` / `auth.ts` 用户类型等错误，确保 `npm run build` 通过。
- **更新对比报告**：在 `ISO_11179-3_2023_Comparison_Report.md` 第 8 章中标记配对上下文必填、最小上下文/配对提示、PREFERRED 唯一性、命名空间/命名约定展示等项为“已对齐”或“待对齐”。

## Capabilities

### New Capabilities

- `frontend-pairing-context-mandatory`：前端配对编辑器要求必须选择上下文，与 8.6.1 `context_for_pairing` 的“恰好一个”语义一致。
- `frontend-minimum-context-or-pairing-warning`：指称/定义行若未关联上下文且未参与配对，则给出内联警告，覆盖 8.4.1.4 与 8.4.2.4 约束。
- `frontend-preferred-acceptability-uniqueness`：检测同一项同一作用域（上下文或语言）下多个 PREFERRED 的冲突，覆盖 8.7.1.3 约束。
- `frontend-per-line-namespace-convention`：在编辑器与详情表格中展示并可选编辑指称的 `designation_namespace` 与 `naming_convention_conformance`，覆盖 8.6.2 与 8.6.5。
- `frontend-association-acceptability-display`：在关系表格的上下文标签中展示 `Designation_Context` / `Definition_Context` 关联类上的 `acceptability`，覆盖 8.5.1 与 8.5.2。
- `frontend-context-usage-grouping`：在上下文引用弹窗中按所属项分组并展示关联类型与可接受性。
- `frontend-ts-build-cleanup`：修复既有 TypeScript 类型错误，使关系 UI 变更可编译、可验证。
- `backend-pairing-context-not-null`：后端将 `designation_definition_pairing.context_id` 设为 `NOT NULL` 并拒绝空值提交，配套 Flyway 迁移与数据修复脚本。
- `backend-textline-namespace-convention-dto`：在 `TextLineResponse` 和关系聚合端点中返回 `namespaceIds` 与 `namingConventionIds`，为前端展示提供数据。
- `documentation-11179-ch8-consistency`：更新 `ISO_11179-3_2023_Comparison_Report.md` 第 8 章状态。

### Modified Capabilities

（无现有 OpenSpec 规范的需求行为需要修改；本补充计划仅针对前期变更未覆盖的关系语义一致性缺口。）

## Impact

- `metadata-registry-ui`：修改 `MultilingualTextEditor.vue`、`DesignationDefinitionPairingEditor.vue`、`RelationshipTable.vue`、`MetadataDetailView.vue`、`ContextUsageDialog.vue`、`ContextList.vue`，新增/扩展类型定义与 API 资源；批量修复 `*List.vue`、`AppLayout.vue`、`auth.ts` 等既有类型错误。
- `metadata-registry-web`：扩展 `TextLineResponse`、`ItemRelationshipResponse`、`DesignationDefinitionPairingResponse`；在 `DesignationDefinitionPairing` 实体/服务层增加 `context_id` 非空校验；可能新增 `TextLineContextResponse` 的 `acceptability` 字段；新增 Flyway 迁移将 `designation_definition_pairing.context_id` 设为 `NOT NULL`。
- `metadata-registry-common-facilities`：若需实体层面强制，给 `DesignationDefinitionPairing.context` 增加 `nullable = false`（需配合迁移）。
- 数据库：新增 Flyway 迁移 `Vxxx__pairing_context_not_null.sql`；若现有空数据，需先补充默认上下文或清理。
- 文档：更新 `ISO_11179-3_2023_Comparison_Report.md` 第 8 章状态。
