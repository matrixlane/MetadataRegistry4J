# measure-class-dimensionality-association Specification

## Purpose
补齐计量类（Measure_Class）→ 维度（Dimensionality）方向的 `dimensionality_measure_class` 关联（ISO/IEC 11179-31:2023 §7.5.3.1，0..* ↔ 0..*）维护契约：计量类写路径可维护该关联（空集=清空），响应携带已关联维度，前端计量类页提供编辑入口；与既有维度→计量类方向写入互通（共享同一中间表，任一侧写入后另一侧视角一致）。

## Requirements

### Requirement: 计量类维度关联维护 API

`POST /api/v1/measure-classes` 与 `PUT /api/v1/measure-classes/{id}` 的请求体 MUST 接受计量类内容与 `dimensionalityIds`（维度 id 列表，0..*，对应 ISO/IEC 11179-31:2023 §7.5.3.1；缺省或空集表示清空关联）。响应 MUST 在计量类同构字段（id/label/sourceUri/designations/definitions/meta）上附加 `dimensionalities`（元素含 id 与 label）。列表中任一维度 id 不存在时 MUST 返回 404 语义错误。更新为整组替换语义，允许替换为空集。

#### Scenario: 创建时携带维度

- **WHEN** 创建计量类提交 `dimensionalityIds=[d1, d2]`
- **THEN** 响应 `dimensionalities` 含 d1、d2 的 id 与 label

#### Scenario: 清空关联

- **WHEN** 更新计量类提交 `dimensionalityIds=[]`（或不提交该字段）
- **THEN** 响应 `dimensionalities` 为空，且这些维度的详情不再包含该计量类

#### Scenario: 包含不存在的维度

- **WHEN** 提交 `dimensionalityIds=[d1, 不存在 id]`
- **THEN** 返回 404 语义错误，计量类未被创建/修改

#### Scenario: 与维度侧视角互通

- **WHEN** 经计量类接口把计量类 MC 关联到维度 D 后查询 `GET /api/v1/dimensionalities/{D}`
- **THEN** 响应 `measureClasses` 含 MC

### Requirement: 计量类页维度关联编辑

计量类管理页 MUST 在新建/编辑表单中提供维度多选选择器，并在列表中展示每个计量类已关联的维度数量；编辑时 MUST 回填已关联维度。

#### Scenario: 编辑回填

- **WHEN** 打开已关联 2 个维度的计量类编辑对话框
- **THEN** 维度选择器显示该 2 个维度为已选状态

#### Scenario: 列表计数

- **WHEN** 浏览计量类列表
- **THEN** 每行显示该行计量类已关联的维度数量
