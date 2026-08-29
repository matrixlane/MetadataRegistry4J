# Proposal: remove-cd-permitted-values-fieldname

## Why

经与 ISO/IEC 11179-31:2023 原文逐条核实（Milvus 驻留标准全文）：

- §7.4.2.6.4 明确 Reference_Enumerated_Conceptual_Domain **"has no additional attributes"**；`permitted_values_fieldname` 仅是 Reference_Enumerated_Value_Domain 的必填属性（§7.4.2.13.4 Table 7）。
- §7.4.2.8.4 Table 5 的 Value_Domain 属性（datatype、valid_period、format、maximum_character_quantity、unit_of_measure）定义在父类，**所有子类继承**，包括 Reference_Enumerated_Value_Domain。

现行实现（源自既有幻觉注释）偏离了两处：

1. `ReferenceEnumeratedConceptualDomain` 携带非标准字段 `permittedValuesFieldname`（DTO / 校验 / 前端外部源卡片全链路）。
2. `ValueDomainService.applyCommon` 对 ReferenceEnumeratedValueDomain 特判清空 `maximumCharacterQuantity` / `format`，前端也将这两个公共字段限定为 DESCRIBED-only。

## What Changes

- **BREAKING（概念域 API）**：`ConceptualDomainRequest` / `ConceptualDomainResponse` 移除 `permittedValuesFieldname`；`type=REFERENCE_ENUMERATED` 时不再校验该字段。
- `ReferenceEnumeratedConceptualDomain` 实体移除 `permittedValuesFieldname` 列，回归标准的空子类。
- 概念域前端：移除引用枚举型的"外部源"卡片（卡片内仅该字段输入框）；保留两级类型选择器。
- 值域后端：`applyCommon` 对全部子类统一写入 `maximumCharacterQuantity` / `format`，删除引用枚举特判。
- 值域前端：数据类型 / 计量单位 / 最大字符数 / 格式 四个公共字段对所有类型渲染；`概念域` picker 仍仅 DESCRIBED 显示；submitFn 不再对 REFERENCE 清空公共字段。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `value-domain-kind-enumeration`：概念域引用枚举型不再携带外部源字段；值域公共字段对所有子类开放。

## Impact

- 后端：`ReferenceEnumeratedConceptualDomain`（实体）、`ConceptualDomainRequest/Response`、`ConceptualDomainService`（create/update/cleanup/toResponse）、`ConceptualDomainController`（OpenAPI 描述）、`ValueDomainService.applyCommon`。
- 前端：`ConceptualDomainList.vue`（移除外部源卡片与脚本引用）、`ValueDomainList.vue`（公共字段移出 DESCRIBED-only 分支）。
- 测试：`ConceptualDomainServiceTest`（删 1 改 3）、`ValueDomainServiceTest`（改 1）。
- 数据库：Hibernate `ddl-auto:update` 不自动删列；`reference_enumerated_conceptual_domain.permitted_values_fieldname` 残留列无害（无实体映射），后续可手工清理。
- 不影响值域侧 `permittedValuesFieldname`（§7.4.2.13.4 Table 7 必填属性，保留）。
