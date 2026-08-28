## Why

按 ISO/IEC 11179-31:2023 §7.4.2，Value_Domain 与 Conceptual_Domain 同构，都是三层元模型：根 `Xxx_Domain` 下并列两个抽象子类 `Described_Xxx_Domain`（按描述/规则/区间刻画，无显式许可值集合）与 `Enumerated_Xxx_Domain`（按一组许可值 / 值含义刻画）；`Enumerated_Xxx_Domain` 又是抽象类，再细分为 `Local_Enumerated_Xxx_Domain`（集合就登记在本注册库内）与 `Reference_Enumerated_Xxx_Domain`（集合引用自一个外部源，需记录字段名）。本注册库 `metadata-registry-data-specification` 模块的 4 个相关实体类（`ConceptualDomain` / `ValueDomain` / `DataElementConcept` / `DataElement`）均已按这套元模型建好：
- `ConceptualDomain` → `DescribedConceptualDomain` / `EnumeratedConceptualDomain` → `LocalEnumeratedConceptualDomain` / `ReferenceEnumeratedConceptualDomain`
- `ValueDomain` → `DescribedValueDomain` / `EnumeratedValueDomain` → `LocalEnumeratedValueDomain` / `ReferenceEnumeratedValueDomain`

但 4 个前端列表页都存在 UI 缺口：
1. `ConceptualDomainList.vue` 仅暴露两选项（"描述型"/"局部枚举"），缺"枚举型"中间层与"引用枚举型"分支。
2. `ValueDomainList.vue` 同上。
3. `DataElementConceptList.vue` 的"概念域"选择器与 `DataElementList.vue` 的"值域"选择器直接把候选清单渲染为标签，不显示每个候选的 kind（描述型/本地枚举型/引用枚举型），让引用枚举这类新增类型在全链路不可发现。

后端 4 个对应 service 同样缺口：`ConceptualDomainType` 枚举只有 `DESCRIBED` / `LOCAL_ENUMERATED`；`ReferenceEnumeratedConceptualDomain` 与 `ReferenceEnumeratedValueDomain` 都是空类，没有承接 ISO 11179-31 §7.4.2.6 / §7.4.2.13 必填的字段。本次变更把 4 个实体一并拉齐到 ISO 11179-31 的三层结构 + 全链路 kind 可发现性。

## What Changes

- **概念域（Conceptual Domain）镜像值域**：完整 3 层 UI 重构 + 后端扩展：
  - `ConceptualDomainType` 新增 `REFERENCE_ENUMERATED`。
  - `ReferenceEnumeratedConceptualDomain` 增加必填 `permitted_values_fieldname`（按 §7.4.2.6 的 `fieldname_for_value_meanings`）。
  - `ConceptualDomainRequest` / `ConceptualDomainResponse` record 新增 `permittedValuesFieldname`。
  - `ConceptualDomainService.create/update` 的 switch 增 `REFERENCE_ENUMERATED -> new ReferenceEnumeratedConceptualDomain()` 分支；`applyTypeSpecific` 增引用枚举分支；`toResponse` 增引用枚举分支。
  - `ConceptualDomainList.vue` 改为两级选择器（`kind` = `DESCRIBED` / `ENUMERATED`；二级 `enumKind` = `LOCAL` / `REFERENCE`），对应三种界面分支：
    - 描述型：保留现有"来源 URI"与"概念域定义"卡片。
    - 本地枚举型：保留现有"值含义"编辑卡片（增加/删除 VM、有效期、设计/定义多语行）。
    - 引用枚举型（新增）：外部源卡片，含 `permittedValuesFieldname` 必填输入框。
  - 列表类型列 `el-tag` 增"引用枚举型"分支。
- **值域（Value Domain）三层 UI**：在已有 v1 基础上沿用（见 spec `value-domain-kind-enumeration`）。
- **数据元概念（Data Element Concept）概念域选择器 kind 标签化**：`DataElementConceptList.vue` 的"概念域"picker 下拉项 MUST 在 label 旁渲染一个 kind `el-tag`（描述型/本地枚举型/引用枚举型），保持现有 picker 多选语义不变；同时支持按 kind 过滤（picker 头部加 kind 多选筛选）。
- **数据元（Data Element）值域选择器 kind 标签化**：`DataElementList.vue` 的"值域"picker 下拉项 MUST 在 label 旁渲染 kind `el-tag`，picker 头部增 kind 多选筛选。
- **概念域 list 接口增 `type` 过滤参数**：`GET /api/v1/conceptual-domains` 接受可选 `type` query param，按 type 过滤（已为值域 picker 提供引用枚举概念域选项而存在）；值域 list 接口同步增 `type` 过滤参数（本期一并补齐，便于前端按 kind 筛选 picker 候选）。
- **概念域 / 值域 list 接口增 `type` 过滤参数**：与 `value-domain-kind-and-enum-kind` v1 一致；本期确保两个接口都支持。
- **既有 `ConceptualDomain` UI 的描述型与本地枚举型分支**：保留现有"来源 URI"、"概念域定义"卡片；本地枚举的"值含义"卡片不动；引用枚举引入外部源字段。
- **i18n**：本期仅用中文直字符串（沿用 v1 决策）；不引入新 i18n key。

## Capabilities

### New Capabilities

- `value-domain-kind-enumeration`：值域三层分类（described/enumerated/local/reference）与对应界面分支，外部源字段必填。
- `conceptual-domain-kind-enumeration`：概念域三层分类（described/enumerated/local/reference）与对应界面分支，外部源字段必填。
- `domain-kind-picker-visibility`：数据元 / 数据元概念的关联选择器（值域 / 概念域）下拉项 kind 标签化与按 kind 多选筛选。

### Modified Capabilities

无（既有 spec 集合里没有同名能力；本变更全部为新增）。

## Impact

- **后端**：
  - `metadata-registry-data-specification`：
    - `ReferenceEnumeratedConceptualDomain` 新增 `permitted_values_fieldname`（@Column 非空）与 `referenced_value_domain_id` 概念外键留空（概念域与值域的相互引用本期不在 spec 范围内）；新字段由 Hibernate `ddl-auto:update` 加列。
    - `ReferenceEnumeratedValueDomain` 沿用 v1（`permitted_values_fieldname` + `referenced_conceptual_domain`）。
  - `metadata-registry-web`：
    - `ConceptualDomainType` 枚举扩展（DESCRIBED / LOCAL_ENUMERATED / REFERENCE_ENUMERATED）。
    - `ConceptualDomainRequest` / `ConceptualDomainResponse` record 增加 `permittedValuesFieldname` 字段。
    - `ConceptualDomainService.create/update/applyTypeSpecific/toResponse` 增加引用枚举分支。
    - `ValueDomainService` / `ValueDomainController` 沿用 v1。
    - `ConceptualDomainController.list` 与 `ValueDomainController.list` 均接受可选 `type` query param；service 层增 JPA Specification 过滤。
- **前端**：
  - `metadata-registry-ui/src/views/value-domain/ValueDomainList.vue`：沿用 v1；本期可能微调 picker kind 标签（与 CD 一致化）。
  - `metadata-registry-ui/src/views/conceptual-domain/ConceptualDomainList.vue`：完全镜像 ValueDomainList 的两层选择器 + 三分支；类型列 `el-tag` 增"引用枚举型"。
  - `metadata-registry-ui/src/views/data-element/DataElementList.vue`："值域"picker 增 kind 标签与 kind 多选过滤。
  - `metadata-registry-ui/src/views/data-element-concept/DataElementConceptList.vue`：补"概念域"picker（目前是 objectClassId / propertyId 两个 UUID 输入框；本期先调整为标准下拉选择器，并增加 kind 标签 + kind 多选过滤）。
  - `metadata-registry-ui/src/api/resources.ts`：`conceptualDomainApi.list` 与 `valueDomainApi.list` 增 `type` query 透传。
- **数据模型**：
  - `reference_enumerated_value_domain` 表自动新增 v1 设计的两列。
  - `reference_enumerated_conceptual_domain` 表自动新增 `permitted_values_fieldname` 列（与 v1 同模式）。
- **i18n**：本期仅用中文直字符串。
- **数据库迁移**：不写 Flyway（Hibernate 可表达）。
- **依赖**：无新增依赖。
- **测试**：
  - `ConceptualDomainServiceTest`：增引用枚举分支覆盖。
  - `ValueDomainServiceTest`：沿用 v1。
  - `ConceptualDomainControllerTest` / `ValueDomainControllerTest`：增 `type` 过滤参数测试。
  - Playwright：`tests/e2e/conceptual-domain.spec.ts`、`value-domain.spec.ts`、`data-element.spec.ts`、`data-element-concept.spec.ts` 覆盖对应 UI 分支。
- **权限**：本期不收紧既有读/写权限评估。

## 范围同步说明

本期把 4 个实体一并处理的目标是：让 ISO/IEC 11179-31 §7.4.2 的三层元模型在 CD / VD 全链路可见，并在 DE / DEC 的关联选择器里可发现；不引入 ISO 标准外的 DE / DEC 子类（标准中 DE / DEC 是 flat item，无 described/enumerated 变体；它们的"kind"由其关联的 VD / CD 决定）。