## ADDED Requirements

### Requirement: MultilingualTextEditor 支持默认语言与可选字段
`MultilingualTextEditor` 在过渡期间继续用于 `ItemFormDialog.vue` 时，SHALL 支持 `defaultLanguageId?: string` prop；语言选择器和来源文档选择器 SHALL 为可选；新增行 SHALL 默认填充默认语言（若提供）。

#### Scenario: 新增行使用默认语言
- **WHEN** 用户点击“添加”按钮且父组件传入了 `defaultLanguageId`
- **THEN** 新行 SHALL 默认填充 `defaultLanguageId`，且语言下拉显示该默认值

#### Scenario: 语言字段可选
- **WHEN** 用户填写文本后清空语言选择器
- **THEN** 组件 SHALL 允许该行存在，并在触发 `update:modelValue` 时保留空值，由父组件在提交前补回默认值

#### Scenario: 来源文档可选
- **WHEN** 用户未选择来源文档
- **THEN** 组件 SHALL 允许提交，`sourceId` 为 `null` 或 `undefined`

#### Scenario: 保留孤立行与 PREFERRED 冲突校验
- **WHEN** 用户操作指称或定义行
- **THEN** 组件 SHALL 继续通过 `conflicts` 事件上报 PREFERRED 冲突与孤立行警告，行为与本变更前保持一致
