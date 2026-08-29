## Why

数据元 / 数据元概念 / 概念域 / 值域 4 个 Item 在新建 / 编辑表单中都有"关联到其它 Item"的场景（数据元选值域 / 数据元概念选对象类+特性+概念域 / 概念域选父概念域 / 等等），但当前的实现是简陋的 `<el-select filterable>`：候选项的 label 是 `r.label || r.identifier || r.id`，结果在 identifier 与 label 缺失时**直接退化为 UUID**，既不可读也不可搜索；filterable 仅做前端字符串包含匹配，跨 entity_type 搜索、namespace 范围限定、精确标识符匹配全都没有；类型过滤要每个 Item 单独写 ad-hoc `<el-checkbox-group>`，体验不统一。

ISO/IEC 11179-7 §7 明确要求元数据注册库在引用时保留可追溯的标识符（业务标识符 `VD.00001` 而非 UUID），用户体验也应该是"看到即所得"的标识符驱动搜索。

本次变更：新建一个共享 `<SmartItemPicker>` 组件，复用已存在的 `GET /api/v1/metadata/search` 跨实体搜索端点，加 `namespaceId` 过滤 + smart-query 解析（`NS.ID` 精确匹配 / 当前命名空间 ID 精确匹配 / 自由文本模糊匹配），所有 4 个 Item 的关联 picker 统一接入。

## What Changes

- **新增共享 `<SmartItemPicker>` 组件**（`metadata-registry-ui/src/components/SmartItemPicker.vue`）：基于 el-select + el-option + 远程搜索 + 类型 chip 过滤 + 命名空间下拉。选中项与候选项统一以 `<type>.<identifier>` 为主、`<label>` 为副展示。
- **复用 `/api/v1/metadata/search`** 作为 intellisense 后端（已有跨实体搜索 + scoped_identifier 模糊匹配 + type 过滤）。
- **`/metadata/search` 端点增强**：新增可选 `namespaceId` query param（限定到当前或指定命名空间）。type 过滤已支持。**新增 `metadataSearchApi.searchByQuery({ q, namespaceId?, type?, size })`** 顶层便捷方法（保持与 `/metadata/search` 一致的返回）。
- **Smart-query 解析**：前端解析用户的输入：
  - `NS.ID`（如 `MD.VD.00001`）→ 拆为 namespace shorthand prefix + identifier 精确匹配
  - 单段纯字母数字（如 `VD.00001`）→ 在当前活动命名空间下做 identifier 精确 / 前缀匹配
  - 其它（如 `身高`）→ 走现有 `q` 自由文本模糊匹配
  - 解析结果附在请求参数 `exactMatch=true&identifier=...&namespaceShorthand=...` 上，后端据此切换查询模式
- **后端 `metadata/search` 新增精确匹配路径**：当 `exactMatch=true` 时，按 `identifier` + `namespaceShorthand` 组合做等值查询；返回结果按匹配度排序（精确 > 前缀 > 模糊）。
- **统一接入 4 个 Item 表单**：
  - `ConceptualDomainList.vue`：替换"概念域" / "值域" / "对象类" / "特性" / "单位" 等 picker（CD 仅值域 + 引用概念域，VD 含 CD / 数据类型 / 单位 / 引用 CD）
  - `ValueDomainList.vue`：替换"概念域" / "数据类型" / "单位" / "引用枚举概念域" picker
  - `DataElementConceptList.vue`：替换"对象类" / "特性" / "概念域" picker
  - `DataElementList.vue`：替换"数据元概念" / "值域" picker
  - 共 9+ 处 picker 全部接入新组件
- **旧 `<el-select>` fallback**：保留作为非跨实体场景（如在表单里选 enum 字符串值）的兜底；SmartItemPicker 仅用于 Item-to-Item 关联。
- **i18n**：新增 key `smartPicker.*`（placeholder、empty、loading、typeFilter.*、scope.*），中英文双语。
- **OpenSpec 上一轮的 scope**：本期**不改动**值域 / 概念域的三层 kind 选择器、引用枚举字段；这些是上一轮 `domain-three-tier-ui` 已落地的能力，本期只换 picker UI 容器。

## Capabilities

### New Capabilities

- `smart-item-picker`：跨 Item 类型统一关联 picker 模式（默认显示业务标识符 + 智能搜索 + 类型 / 命名空间过滤 + 精确匹配）。

### Modified Capabilities

无。

## Impact

- **后端**：
  - `MetadataSearchController` / `MetadataSearchService`：新增 `namespaceId` query param；新增 `exactMatch` + `identifier` + `namespaceShorthand` 精确匹配分支（沿用现有 JdbcTemplate 原生 SQL，方言中立）。
  - 新增 DTO：`MetadataSearchHit`（轻量搜索结果行，PK + identifier + label + type + namespace），复用于 picker 渲染。
- **前端**：
  - 新增 `metadata-registry-ui/src/components/SmartItemPicker.vue`（共享组件）
  - `metadata-registry-ui/src/api/resources.ts` 新增 `metadataSearchApi`
  - `metadata-registry-ui/src/composables/useSmartPicker.ts`（debounce + 请求缓存 + 选后回填）
  - 4 个 Item 表单 9+ 处 picker 改造
  - `metadata-registry-ui/src/i18n/zh-CN.json` + `en-US.json` 加 `smartPicker.*` 文案
- **数据模型**：无 schema 变更；纯查询路径扩展。
- **Flyway**：不写（Hibernate 未变更）。
- **依赖**：无新增依赖（复用 Element Plus el-select / el-checkbox-group / el-tag）。
- **测试**：
  - 后端 `MetadataSearchServiceTest` 新增 6+ 用例：精确 identifier 匹配、namespace 限定、跨实体 keyword、type 过滤、NS.ID 解析、空结果。
  - 前端 `SmartItemPicker.spec.ts` 新增：输入触发搜索 / type 切换重查 / 选中回填 / 精确匹配短输入。
  - Playwright `tests/e2e/item-picker.spec.ts` 覆盖 4 个 Item 的表单。

## 范围外

- 不引入新的标识符算法（沿用现有 TemplateGenerationStrategy / SequenceRegistry）。
- 不实现"实时输入流 intellisense"（即输入到一半触发搜索）— 本期仅做"用户停止输入 ~300ms 后"单次搜索（标准前端防抖模式）。
- 不持久化"最近使用" / "收藏"。
- 不动 OpenSpec `domain-three-tier-ui` 已落地的能力（kind 选择器、引用枚举外部源字段）。
- 概念域 / 值域 概念子项（VM / PV）的编辑卡片、本期不在范围内。

## 涉及 4 个实体一览

| 实体 | 待接入 picker 字段 | 后端实体类型（itemType） |
|------|-------------------|--------------------------|
| DataElement | `dataElementConceptId`, `valueDomainId` | DataElement |
| DataElementConcept | `objectClassId`, `propertyId`, `conceptualDomainIds` (多选) | DataElementConcept |
| ConceptualDomain | `describedConceptualDomainId` | ConceptualDomain |
| ValueDomain | `describedConceptualDomainId`, `dataTypeId`, `unitOfMeasureId`, `referencedConceptualDomainId` | ValueDomain |

通用配置项 `objectClassApi` / `propertyApi` / `dataTypeApi` / `unitOfMeasureApi` / `namespaceApi` 也复用 SmartItemPicker（itemType 传 O / P / DT / UOM / NS 即可）。