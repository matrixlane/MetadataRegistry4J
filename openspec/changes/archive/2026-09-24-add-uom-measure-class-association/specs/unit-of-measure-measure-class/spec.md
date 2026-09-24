# unit-of-measure-measure-class Specification

## Purpose

补齐计量单位（Unit_ofMeasure）与计量类（Measure_Class）之间 `unit_of_measure_class` 关联（ISO/IEC 11179-31:2023 §7.5.3.2）的端到端契约：计量单位写路径必须可维护该关联且强制 UoM 侧 1..* 基数（每个计量单位至少归属一个计量类），计量类在被计量单位引用时不可删除，前端计量单位页提供关联编辑入口。

## ADDED Requirements

### Requirement: 计量单位关联维护 API

`POST /api/v1/units-of-measure` 与 `PUT /api/v1/units-of-measure/{id}` 的请求体 MUST 接受计量单位内容与 `measureClassIds`（计量类 id 列表，**1..***，对应 ISO/IEC 11179-31:2023 §7.5.3.2 的 UoM 侧基数）。响应 MUST 在计量单位同构字段（id/label/sourceUri/designations/definitions/meta）上附加 `measureClasses`（元素含 id 与 label）。`measureClassIds` 为 null 或空集时 MUST 拒绝并返回 422 语义错误，消息注明标准依据（计量单位必须至少归属一个计量类）；列表中任一 id 不存在时 MUST 返回 404 语义错误。更新为整组替换语义，且 MUST NOT 允许清空（清空即触发上述 422）。

#### Scenario: 创建时携带计量类

- **WHEN** 创建计量单位提交 `measureClassIds=[mc1, mc2]`
- **THEN** 响应 `measureClasses` 含 mc1、mc2 的 id 与 label

#### Scenario: 不携带计量类被拒绝

- **WHEN** 创建或更新计量单位提交 `measureClassIds=[]`（或不提交该字段）
- **THEN** 返回 422 语义错误"计量单位必须至少归属一个计量类"，计量单位未被创建/修改

#### Scenario: 包含不存在的计量类

- **WHEN** 提交 `measureClassIds=[mc1, 不存在 id]`
- **THEN** 返回 404 语义错误，计量单位未被创建/修改

#### Scenario: 更新整组替换

- **WHEN** 已归属 [mc1, mc2] 的计量单位更新提交 `measureClassIds=[mc3]`
- **THEN** 响应 `measureClasses` 仅含 mc3

### Requirement: 计量类删除保护

删除计量类时，若其被任一计量单位引用（`unit_of_measure_measure_class` 存在关联行），系统 MUST 拒绝删除并返回含引用数的 409 语义业务错误；未被计量单位引用时 MUST 删除成功。维度对计量类的引用清理行为不属于本需求范围，维持既有行为。

#### Scenario: 被计量单位引用

- **WHEN** 计量类 MC 被 2 个计量单位引用时调用删除
- **THEN** 返回业务错误"计量类正被 2 个计量单位引用，无法删除"，计量类保留

#### Scenario: 无引用删除

- **WHEN** 计量类未被任何计量单位引用时调用删除
- **THEN** 删除成功

### Requirement: 计量单位页关联编辑

计量单位管理页 MUST 在新建/编辑表单中提供计量类多选选择器，并在列表中展示每个计量单位已关联的计量类数量；编辑时 MUST 回填已关联计量类。

#### Scenario: 编辑回填

- **WHEN** 打开已关联 2 个计量类的计量单位编辑对话框
- **THEN** 计量类选择器显示该 2 个计量类为已选状态

#### Scenario: 列表计数

- **WHEN** 浏览计量单位列表
- **THEN** 每行显示该行计量单位已关联的计量类数量
