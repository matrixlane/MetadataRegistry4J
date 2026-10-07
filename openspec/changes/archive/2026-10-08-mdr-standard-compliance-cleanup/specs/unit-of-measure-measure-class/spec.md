# unit-of-measure-measure-class Delta Specification

## MODIFIED Requirements

### Requirement: 计量类删除保护

删除计量类时，若其被任一计量单位引用（`unit_of_measure_class` 存在关联行），系统 MUST 拒绝删除并返回含引用数的 409 语义业务错误；未被计量单位引用时 MUST 删除成功。维度对计量类的引用清理行为不属于本需求范围，维持既有行为。（关联表物理名随标准对齐由 `unit_of_measure_measure_class` 更名为 `unit_of_measure_class`，ISO/IEC 11179-31:2023 §7.5.3.2；API 行为不变。）

#### Scenario: 被计量单位引用

- **WHEN** 计量类 MC 被 2 个计量单位引用时调用删除
- **THEN** 返回业务错误"计量类正被 2 个计量单位引用，无法删除"，计量类保留

#### Scenario: 无引用删除

- **WHEN** 计量类未被任何计量单位引用时调用删除
- **THEN** 删除成功
