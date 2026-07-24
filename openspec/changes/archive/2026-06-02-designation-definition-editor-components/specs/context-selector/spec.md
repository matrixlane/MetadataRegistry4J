## ADDED Requirements

### Requirement: ContextSelector 支持远程搜索上下文
组件 SHALL 渲染一个可远程搜索的 `el-select`，调用 `contextApi.options({ q: query })` 获取选项列表，通过 `v-model` 双向绑定选中的 `contextId`（字符串或 undefined）。

组件 SHALL 支持 `clearable`（默认 `true`）、`size`（默认 `'small'`）、`placeholder`（默认"选择上下文（可选）"）props。

组件 SHALL 在输入关键词时展示 loading 状态，请求完成后更新选项。

组件在挂载时 SHALL 使用空字符串查询预加载初始选项，使下拉框首次打开即有内容可选。

#### Scenario: 挂载时预加载
- **WHEN** 组件被挂载
- **THEN** 组件 SHALL 调用 `contextApi.options({ q: '' })` 并将结果填充到选项列表

#### Scenario: 输入关键词触发远程搜索
- **WHEN** 用户在搜索框中输入"政务"
- **THEN** 组件 SHALL 调用 `contextApi.options({ q: '政务' })` 并更新选项

#### Scenario: 选中并绑定上下文
- **WHEN** 用户选择某上下文选项
- **THEN** `update:modelValue` 事件 SHALL 以该选项的 `id` 字符串触发

#### Scenario: 清除上下文
- **WHEN** clearable 为 true 且用户点击清除按钮
- **THEN** `update:modelValue` 事件 SHALL 以 `undefined` 触发

### Requirement: ContextSelector 与 AcceptabilitySelect 组合使用
当父组件需要同时记录上下文和可接受性时，SHALL 通过 slot 或双 prop 方式在同一行中组合使用 `ContextSelector` 与 `AcceptabilitySelect`，而不是在 ContextSelector 内部内置 AcceptabilitySelect。

#### Scenario: 外部组合两个组件
- **WHEN** 父组件在同一 flex 行中同时使用 `<ContextSelector>` 和 `<AcceptabilitySelect>`
- **THEN** 两个组件各自独立响应 v-model 事件，互不干扰
