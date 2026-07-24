## ADDED Requirements

### Requirement: DesignationEditor 编辑指称列表
组件 SHALL 接受 `modelValue: TextLineForm[]` prop，渲染一个可增删的指称列表，每条指称行包含：语言选择器、指称文本输入框、删除按钮，以及可展开的上下文-可接受性配置区。

组件内部 SHALL 通过 `languageApi.list({ size: 100 })` 在挂载时获取语言选项。

组件 SHALL 在指称文本区域的占位符、添加按钮等处使用"指称"相关的语义标签（如"添加指称"、"输入指称文本"）。

组件 SHALL 通过 `update:modelValue` 事件向父组件通知数据变更，不直接修改 prop（单向数据流）。

每条指称行的上下文-可接受性区域 SHALL 使用 `ContextSelector` 和 `AcceptabilitySelect` 子组件组合渲染。

#### Scenario: 渲染已有指称列表
- **WHEN** 父组件传入包含两条指称的 `modelValue`
- **THEN** 组件 SHALL 渲染两张指称卡片，每张显示对应语言和文本

#### Scenario: 添加新指称
- **WHEN** 用户点击"添加指称"按钮
- **THEN** 组件 SHALL 触发 `update:modelValue`，新数组末尾追加 `{ languageId: '', value: '', contexts: [] }`

#### Scenario: 删除指称
- **WHEN** 用户点击某指称行的删除按钮
- **THEN** 组件 SHALL 触发 `update:modelValue`，返回移除该项后的新数组

#### Scenario: 展开上下文配置
- **WHEN** 用户点击某指称行的上下文展开按钮
- **THEN** 该行 SHALL 展开显示上下文-可接受性配置区，其他行不受影响

#### Scenario: 为指称添加上下文关联
- **WHEN** 用户在某指称行展开区域点击"添加上下文"
- **THEN** 该指称的 `contexts` 数组 SHALL 追加 `{ contextId: '', acceptability: 'ADMITTED' }`

#### Scenario: 删除指称的上下文关联
- **WHEN** 用户点击某上下文关联行的删除按钮
- **THEN** 该指称的 `contexts` 数组 SHALL 移除对应项

### Requirement: DesignationEditor 与 DefinitionEditor 共享相同交互模式
`DesignationEditor` 和 `DefinitionEditor` 的交互行为、视觉结构 SHALL 完全一致，仅语义标签（组件 label）不同，确保用户学习成本最低。

#### Scenario: 两组件视觉一致性
- **WHEN** 同一页面同时展示 `DesignationEditor` 和 `DefinitionEditor`
- **THEN** 两个组件的卡片样式、按钮位置、展开区域布局 SHALL 视觉上完全对称
