# Delta for value-domain-kind-enumeration

## MODIFIED Requirements

### Requirement: 概念域（Conceptual_Domain）创建与编辑采用两级分类选择器

新建或编辑概念域时，前端 MUST 呈现与值域同构的两级联动分类选择器。一级 `kind`（`DESCRIBED` / `ENUMERATED`）；二级 `enumKind` 仅一级为 `ENUMERATED` 时显示（`LOCAL` / `REFERENCE`）。提交 `type` 映射：`DESCRIBED` → `DESCRIBED`；`ENUMERATED + LOCAL` → `LOCAL_ENUMERATED`；`ENUMERATED + REFERENCE` → `REFERENCE_ENUMERATED`。

#### Scenario: 创建态默认进入描述型

- **WHEN** 用户在概念域列表点击"新建"打开对话框
- **THEN** 第一级选择器 MUST 默认为 `DESCRIBED`，第二级选择器 MUST 隐藏；表单 MUST 渲染"来源 URI"输入与概念域定义编辑区；提交体 `type` MUST 为 `DESCRIBED`

#### Scenario: 本地枚举型分支渲染值含义卡片

- **WHEN** 用户组合为 `LOCAL_ENUMERATED`
- **THEN** 表单 MUST 显示"值含义"编辑卡片（含增删 VM、有效期、设计/定义多语行）；其他卡片 MUST 不可见

#### Scenario: 引用枚举型分支无专属字段

- **WHEN** 用户组合为 `REFERENCE_ENUMERATED`
- **THEN** 表单 MUST NOT 渲染任何引用枚举专属卡片或字段（ISO/IEC 11179-31:2023 §7.4.2.6.4：Reference_Enumerated_Conceptual_Domain "has no additional attributes"）；值含义卡片 MUST 不可见

#### Scenario: 编辑既有引用枚举概念域反查两级初值

- **WHEN** 用户点击列表中类型为 `REFERENCE_ENUMERATED` 的概念域行打开编辑对话框
- **THEN** 第一级选择器 MUST 自动设为 `ENUMERATED`，第二级选择器 MUST 自动设为 `REFERENCE`

### Requirement: 概念域后端枚举与请求响应字段扩展

`ConceptualDomainType` MUST 包含 `DESCRIBED` / `LOCAL_ENUMERATED` / `REFERENCE_ENUMERATED` 三值。`ConceptualDomainRequest` / `ConceptualDomainResponse` MUST NOT 携带任何 Reference_Enumerated_Conceptual_Domain 专属字段（依据 §7.4.2.6.4，该子类无附加属性）；`permitted_values_fieldname` 仅属于 Reference_Enumerated_Value_Domain（§7.4.2.13.4 Table 7）。

#### Scenario: ConceptualDomainType 枚举响应包含三个值

- **WHEN** 客户端读取概念域列表
- **THEN** 任一返回行的 `type` 字段 MUST 严格属于 `{DESCRIBED, LOCAL_ENUMERATED, REFERENCE_ENUMERATED}`；前端类型列 MUST 按该枚举渲染"描述型"/"本地枚举型"/"引用枚举型"三个标签

## REMOVED Requirements

### Requirement: 引用枚举概念域数据模型承载外部源字段

**Reason**: ISO/IEC 11179-31:2023 §7.4.2.6.4 明确 Reference_Enumerated_Conceptual_Domain "has no additional attributes"；`permitted_values_fieldname` 仅是 Reference_Enumerated_Value_Domain 的必填属性（§7.4.2.13.4 Table 7）。既有需求基于幻觉注释，与标准矛盾，整体移除。
