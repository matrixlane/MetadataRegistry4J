## Context

前期变更（`iso-11179-3-ch8-figure6-relationship-completion`、`iso-11179-3-ch8-figure6-frontend-interaction-supplement`）已经让 ISO/IEC 11179-3:2023 第 8 章的元模型和主要前端交互落地：

- 后端实体已覆盖 `Designation`、`Definition`、`Context`、`DesignationDefinitionPairing`、`NamingConvention`、`Namespace` 及其关联类；
- 聚合端点 `/api/v1/items/{itemId}/designation-definition-relationships` 返回指称、定义、配对、上下文和命名约定；
- 前端 `MultilingualTextEditor` 支持语言、值、可接受性、上下文、来源文献；
- 前端 `DesignationDefinitionPairingEditor` 支持选择指称、定义、上下文和可接受性；
- `MetadataDetailView` 已拆出“指称 / 定义 / 上下文 / 配对”标签页；
- `ContextList` 支持反向查看引用；
- `BrowserView` 支持上下文和可接受性高级筛选；
- `NamespaceList` 支持多命名约定和三个约束指示器。

但对照标准文本，前端在“关系语义”上仍有偏差：

1. 标准 8.6.1 规定 `context_for_pairing` 是“恰好一个 Context”，而前端把上下文标为“可选”；后端 JPA 也允许 `null`。
2. 标准 8.4.1.4 / 8.4.2.4 要求每个 `Designation` / `Definition` 至少参与一次上下文关联或一次配对；前端没有提示或阻止孤立行。
3. 标准 8.7.1.3 要求同一集合中只能有一个 `PREFERRED`；前端允许同一项下多个 PREFERRED 而不警告。
4. 标准 8.6.2 / 8.6.5 的 `designation_namespace` 和 `naming_convention_conformance` 在前端不可见、不可编辑；`TextLineResponse` 也没有返回这些字段。
5. 标准 8.5.1 / 8.5.2 的关联类 `Definition_Context` / `Designation_Context` 带有 `acceptability` 属性，但前端关系表格只展示上下文标签，不展示关联上的可接受性。
6. `ContextUsageDialog` 目前平铺列表，缺少按所属项分组和关联类型/可接受性的清晰展示。
7. 当前 `npm run build` 存在大量既有 TypeScript 错误，导致新的关系 UI 无法被可靠验证。

本变更将补全这些关系一致性缺口，使前端交互与 Figure 6 的语义对齐，并通过后端 DTO/校验提供数据支撑。

## Goals / Non-Goals

**Goals:**

- 让 `DesignationDefinitionPairingEditor` 中的上下文选择符合标准“恰好一个”的语义，并在后端/数据库层同步禁止空值。
- 在 `MultilingualTextEditor` 中为每条指称/定义行提供“未关联上下文或配对”的实时警告，并默认阻止提交（支持用户显式标记例外）。
- 实现同一作用域下多个 `PREFERRED` 的冲突检测与高亮，覆盖指称、定义和配对。
- 让指称/定义的命名空间与命名约定关系在前端可查看、可编辑，并在关系表格中展示。
- 在关系表格的上下文标签中展示 `Designation_Context` / `Definition_Context` 关联类上的 `acceptability`。
- 改进 `ContextUsageDialog`，按所属项分组并展示关联类型与可接受性。
- 修复既有 TypeScript 构建错误，使 `npm run build` 通过。
- 更新 `ISO_11179-3_2023_Comparison_Report.md` 第 8 章状态。

**Non-Goals:**

- 不重新设计整个元数据编辑工作流；保留现有标签页/弹窗模式。
- 不引入完整的图形化元模型编辑器（如 UML 图）。
- 不修改 `Context`、`Namespace`、`NamingConvention` 等基础实体的核心结构，仅扩展 DTO 与前端展示。
- 不一次性清理所有历史数据问题；仅通过迁移脚本或例外机制处理现有空上下文配对。
- 不扩展后端业务规则到“每个指称必须唯一”等超出标准 8.3.1.5 的范围。

## Decisions

1. **配对上下文必填**：将 `DesignationDefinitionPairingEditor.vue` 的上下文选择器从 `clearable` 改为必填，并为空值时禁用保存。后端 `DesignationDefinitionPairing` 的 `context` 列通过 Flyway 迁移设为 `NOT NULL`，服务层新增非空校验。对于历史空数据，先由迁移脚本分配一个内置“默认注册上下文”或标记为需人工清理，而不是直接阻止迁移。

2. **最小上下文/配对提示采用“警告+例外”模式**：因为历史数据或特殊场景下可能存在暂时孤立行，前端采用软阻止策略：默认不允许提交，但用户可在孤立行上勾选“允许例外”。这样既满足标准约束，又为数据治理留出过渡空间。例外信息随表单一起提交，后端可存储审计字段但不改变核心关系。

3. **PREFERRED 唯一性作用域**：冲突检测的作用域为“同一项 + 同一上下文”。对于没有上下文的直接可接受性，视为“无上下文作用域”；若同一项同一语言存在多个无上下文的 PREFERRED，则冲突。这样与标准“同一集合”语义一致，同时允许不同上下文中各自存在一个 PREFERRED。

4. **命名空间/命名约定放在折叠面板**：`MultilingualTextEditor` 的每条指称/定义行新增可折叠“命名空间与命名约定”区域。与“上下文关联与来源”区域保持一致，避免基础行拥挤。定义行只显示命名空间（标准未定义定义与命名约定的关系），不显示命名约定。

5. **关联类可接受性合并到上下文标签**：`RelationshipTable.vue` 的 `contextColumns` 渲染逻辑把 `contextLabel` 和 `acceptability` 合并为单个标签，例如 `业务上下文 · 首选`。这样避免每行额外增加一列，保持表格紧凑。

6. **DTO 扩展而非新端点**：复用现有的 `/api/v1/items/{itemId}/designation-definition-relationships` 端点，在 `TextLineResponse` 中增加 `namespaceIds` 和 `namingConventionIds`，在 `TextLineContextResponse` 中增加 `acceptability`。避免新增端点带来的前端复杂度。

7. **上下文引用弹窗按项分组**：在 `ContextUsageDialog.vue` 中把 `usages` 数组按 `itemId` 聚合为分组列表，使用 `el-collapse` 或嵌套表格。保持分页在条目级别，避免弹窗内数据过多。

8. **TypeScript 构建清理优先修复 `prop` 类型和 `auth.ts` 字段**：对于 `*List.vue` 中的 `el-table-column prop="..."` 报错，统一使用 `:(prop="('x.y') as any")` 或提取为常量；对于 `auth.ts` 缺少的用户字段，扩展 `User` 类型。不引入全局类型重写，保持改动最小。

## Risks / Trade-offs

- [风险] 强制配对上下文必填可能破坏现有空数据导入/迁移。缓解措施：通过 Flyway 迁移脚本先将空值更新为内置默认上下文，并提供一次性数据审计日志。
- [风险] “孤立行”警告可能与用户现有批量录入流程冲突。缓解措施：提供“允许例外”开关和批量忽略选项，初期作为警告而非硬性拒绝。
- [风险] PREFERRED 冲突检测逻辑需要比较直接可接受性、上下文关联可接受性和配对可接受性，容易产生误判。缓解措施：明确作用域为“同一项 + 同一上下文”，并仅对直接可接受性/配对可接受性做交集检测；上下文关联可接受性只在展示时高亮，不直接参与冲突（因为它表示的是在该上下文下的偏好）。
- [风险] 扩展 `TextLineResponse` 增加 `namespaceIds` / `namingConventionIds` 后，响应体积增大。缓解措施：使用 `Set` 去重，并在关系端点按需加载；`ItemRelationshipController` 已经聚合了命名约定，可复用。
- [风险] 修改 `designation_definition_pairing.context_id` 为 `NOT NULL` 后，旧版客户端可能提交空值导致 400。缓解措施：在 API 错误消息中明确提示，并同步更新前端配对编辑器。
- [风险] TypeScript 修复可能掩盖潜在类型问题。缓解措施：修复后必须运行 `npm run build` 并通过，同时补充组件测试/E2E 覆盖关系 UI。

## Migration Plan

1. **后端**：
   - 扩展 `TextLineResponse`、`TextLineContextResponse`、`ItemRelationshipResponse`（可选）字段。
   - 在 `DesignationDefinitionPairingService` 中增加 `contextId` 非空校验。
   - 新增 Flyway 迁移 `Vxxx__pairing_context_not_null.sql`，将空 `context_id` 更新为默认上下文后再设 `NOT NULL`。
   - 在 `ItemAdministrationService` 中映射 `namespaceIds` 和 `namingConventionIds`。
2. **前端**：
   - 修改 `MultilingualTextEditor.vue`：增加孤立行警告、PREFERRED 冲突检测、命名空间/命名约定折叠区域、上下文关联可接受性展示。
   - 修改 `DesignationDefinitionPairingEditor.vue`：上下文必填、保存按钮禁用逻辑。
   - 修改 `RelationshipTable.vue`：上下文标签展示可接受性。
   - 修改 `MetadataDetailView.vue`：关系表格增加命名空间、命名约定列。
   - 修改 `ContextUsageDialog.vue`：按项分组、显示关联类型与可接受性。
   - 修复 `*List.vue`、`AppLayout.vue`、`auth.ts` 等既有类型错误。
3. **验证**：
   - 运行 `npm run build` 零错误。
   - 运行 `mvn test -pl metadata-registry-web` 中相关测试（`NamespaceConstraintValidatorTest`、`ItemRelationshipControllerTest`、`ContextServiceTest`、`MetadataSearchServiceTest`）通过。
   - 运行 `openspec validate --all` 通过。
4. **文档**：
   - 更新 `ISO_11179-3_2023_Comparison_Report.md` 第 8 章状态。
5. **回滚**：
   - 前端回滚：恢复 Vue 文件和类型定义。
   - 后端回滚：恢复 DTO 和服务代码；数据库回滚需执行 Flyway 修复脚本将 `NOT NULL` 改回 `NULL`（仅在未生产化前）。

## Open Questions

- 历史配对中 `context_id` 为空的数据量是多少？是分配默认上下文，还是要求用户先手动清理？
- “PREFERRED 唯一性”是否需要在后端也强制校验，还是仅在前端给出警告？建议先前端警告，后端可选软校验。
- 是否需要为“孤立行例外”在后端持久化一个布尔字段，还是仅在表单验证阶段使用？
- 指称的 `namingConventionIds` 是否应在用户选择命名空间后自动根据命名空间的 `namingConventionIds` 推断？还是完全由用户手动选择？
