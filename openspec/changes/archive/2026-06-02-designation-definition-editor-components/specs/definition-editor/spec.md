## ADDED Requirements

### Requirement: DefinitionEditor 编辑定义列表
组件 SHALL 接受 `modelValue: TextLineForm[]` prop，渲染一个可增删的定义列表，每条定义行包含：语言选择器、定义文本输入框（多行 textarea 模式）、删除按钮，以及可展开的上下文-可接受性配置区。

组件内部 SHALL 通过 `languageApi.list({ size: 100 })` 在挂载时获取语言选项。

组件 SHALL 在定义文本区域使用 `type="textarea"` 的 `el-input`，支持多行文本输入，占位符应为"输入定义文本"。

组件 SHALL 在添加按钮处显示"添加定义"。

组件 SHALL 通过 `update:modelValue` 事件向父组件通知数据变更，不直接修改 prop（单向数据流）。

每条定义行的上下文-可接受性区域 SHALL 使用 `ContextSelector` 和 `AcceptabilitySelect` 子组件组合渲染。

#### Scenario: 渲染已有定义列表
- **WHEN** 父组件传入包含两条定义的 `modelValue`
- **THEN** 组件 SHALL 渲染两张定义卡片，每张显示对应语言和文本

#### Scenario: 添加新定义
- **WHEN** 用户点击"添加定义"按钮
- **THEN** 组件 SHALL 触发 `update:modelValue`，新数组末尾追加 `{ languageId: '', value: '', contexts: [] }`

#### Scenario: 删除定义
- **WHEN** 用户点击某定义行的删除按钮
- **THEN** 组件 SHALL 触发 `update:modelValue`，返回移除该项后的新数组

#### Scenario: 多行文本输入
- **WHEN** 用户在定义文本框中按下回车
- **THEN** 文本框 SHALL 允许换行（textarea 模式）而非触发表单提交

#### Scenario: 展开上下文配置
- **WHEN** 用户点击某定义行的上下文展开按钮
- **THEN** 该行 SHALL 展开显示上下文-可接受性配置区，其他行不受影响

#### Scenario: 为定义添加上下文关联
- **WHEN** 用户在某定义行展开区域点击"添加上下文"
- **THEN** 该定义的 `contexts` 数组 SHALL 追加 `{ contextId: '', acceptability: 'ADMITTED' }`
