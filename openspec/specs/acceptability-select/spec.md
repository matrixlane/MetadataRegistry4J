# acceptability-select Specification

## Purpose
TBD - created by archiving change designation-definition-editor-components. Update Purpose after archive.
## Requirements
### Requirement: AcceptabilitySelect 渲染可接受性枚举
组件 SHALL 渲染一个 `el-select`，固定提供 PREFERRED、ADMITTED、DEPRECATED、SUPERSEDED、OBSOLETE 五个选项，并通过 `v-model` 双向绑定当前值。

组件 SHALL 支持 `size` prop（`'small' | 'default' | 'large'`，默认 `'small'`），`clearable` prop（布尔，默认 `false`），`placeholder` prop（字符串，默认"可接受性"）。

组件 SHALL 以中文标签显示各枚举值：`PREFERRED → 首选`，`ADMITTED → 认可`，`DEPRECATED → 已弃用`，`SUPERSEDED → 已取代`，`OBSOLETE → 已过时`。

#### Scenario: 渲染五个枚举选项
- **WHEN** 组件被挂载
- **THEN** 下拉菜单中应显示五个选项：首选、认可、已弃用、已取代、已过时

#### Scenario: 双向绑定
- **WHEN** 用户选择"首选"
- **THEN** `update:modelValue` 事件 SHALL 以 `'PREFERRED'` 触发，父组件通过 `v-model` 获得新值

#### Scenario: size prop 传递
- **WHEN** 父组件传入 `size="default"`
- **THEN** `el-select` 的 size 属性 SHALL 为 `'default'`

