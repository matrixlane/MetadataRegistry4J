# standard-inheritance-conformance Specification

## Purpose
TBD - created by archiving change registry-specification-model-correction. Update Purpose after archive.

## Requirements

### Requirement: 实体继承关系必须有标准 Direct superclass 依据
所有元模型实体（`@Entity`/`@StandardReference` 标注类）的继承（extends）关系 MUST 有 ISO/IEC 11179 相应部分 "Direct superclass" 条款依据。凡直接继承 `Item`（或任何标准实体）的类 MUST 登记在继承白名单（守护测试 `StandardInheritanceConformanceTest`）中，且白名单条目 MUST 附带标准条款引用；新增或变更继承边而未同步登记白名单 MUST 导致构建失败。独立根实体（直接继承仅提供标识符的 mapped-superclass `Entity`）不受白名单约束，但标准声明了 Direct superclass 的类 MUST 如实建模。

#### Scenario: 未登记的 Item 继承边导致构建失败
- **WHEN** 开发者新增一个直接 `extends Item` 的实体类而未在守护测试白名单中登记（含标准条款引用）
- **THEN** `StandardInheritanceConformanceTest` 失败，并指出未登记的继承边

#### Scenario: 白名单内继承边通过
- **WHEN** 构建运行时扫描既有实体（Context、Namespace、NamingConvention、ConstraintSet、ItemMapping 等白名单条目）
- **THEN** 守护测试通过，白名单条目带有的标准条款引用可供审计

#### Scenario: 无 Direct superclass 声明的类不得继承 Item
- **WHEN** 某标准类（如 Registry_Specification，ISO/IEC 11179-3:2023 9.4.10 无 Direct superclass 小节）被实现为 `extends Item`
- **THEN** 守护测试失败（硬断言锁定：该类直接超类必须为 `Entity`），防止同类偏差回归
