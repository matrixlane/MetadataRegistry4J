# domain-three-tier-ui

把 ISO/IEC 11179-31:2023 §7.4.2 的三层元模型（Domain → Described/Enumerated → Local/Reference）落到 UI：

- **概念域 (Conceptual Domain)**：两级选择器 + 三分支（描述型 / 本地枚举型 / 引用枚举型）。
- **值域 (Value Domain)**：与概念域同构。
- **数据元 (Data Element)**：值域选择器候选按 kind 标签化（描述型/本地枚举型/引用枚举型）并支持 kind 多选过滤。
- **数据元概念 (Data Element Concept)**：对象类 / 特性改用 picker；新增"概念域"多选 picker（kind 标签化 + 过滤）。

后端：`ConceptualDomainType` / `ValueDomainType` 增 `REFERENCE_ENUMERATED`；`ReferenceEnumeratedConceptualDomain` / `ReferenceEnumeratedValueDomain` 补齐 `permitted_values_fieldname` 必填字段；两个 list 接口均增 `type` 过滤参数。