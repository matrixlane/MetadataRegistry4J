## MODIFIED Requirements

### Requirement: DesignationEditor 编辑指称列表
组件 SHALL 接受 `modelValue: TextLineForm[]` 与可选的 `defaultLanguageId?: string` prop，渲染一个可增删的指称列表。每条指称行包含：语言选择器（可选）、指称文本输入框、删除按钮，以及可展开的上下文-可接受性配置区。当 `defaultLanguageId` 存在时，新增行 SHALL 默认填充该语言。

#### Scenario: 渲染已有指称列表
- **WHEN** 父组件传入包含两条指称的 `modelValue`
- **THEN** 组件 SHALL 渲染两张指称卡片，每张显示对应语言和文本

#### Scenario: 添加新指称时使用默认语言
- **WHEN** 用户点击“添加指称”按钮且 `defaultLanguageId` 已提供
- **THEN** 组件 SHALL 触发 `update:modelValue`，新数组末尾追加 `{ languageId: defaultLanguageId, value: '', contexts: [] }`

#### Scenario: 无默认语言时添加新指称
- **WHEN** 用户点击“添加指称”按钮且未提供 `defaultLanguageId`
- **THEN** 组件 SHALL 触发 `update:modelValue`，新数组末尾追加 `{ languageId: '', value: '', contexts: [] }`

#### Scenario: 删除指称
- **WHEN** 用户点击某指称行的删除按钮
- **THEN** 组件 SHALL 触发 `update:modelValue`，返回移除该项后的新数组

#### Scenario: 展开上下文配置
- **WHEN** 用户点击某指称行的上下文展开按钮
- **THEN** 该行 SHALL 展开显示上下文-可接受性配置区，其他行不受影响

#### Scenario: 为指称添加上下文关联
- **WHEN** 用户在某指称行展开区域点击“添加上下文”
- **THEN** 该指称的 `contexts` 数组 SHALL 追加 `{ contextId: '', acceptability: 'ADMITTED' }`

#### Scenario: 删除指称的上下文关联
- **WHEN** 用户点击某上下文关联行的删除按钮
- **THEN** 该指称的 `contexts` 数组 SHALL 移除对应项

#### Scenario: 语言标识可选
- **WHEN** 用户填写指称文本但清空语言选择器
- **THEN** 组件 SHALL 仍允许该行存在，提交时由父组件或后端按默认语言规则处理；不应对空语言进行前端硬性校验

## ADDED Requirements

### Requirement: DesignationEditor 与 DefinitionEditor 共享相同交互模式
`DesignationEditor` 和 `DefinitionEditor` 的交互行为、视觉结构 SHALL 完全一致，仅语义标签（组件 label）不同，确保用户学习成本最低。

#### Scenario: 两组件视觉一致性
- **WHEN** 同一页面同时展示 `DesignationEditor` 和 `DefinitionEditor`
- **THEN** 两个组件的卡片样式、按钮位置、展开区域布局 SHALL 视觉上完全对称
