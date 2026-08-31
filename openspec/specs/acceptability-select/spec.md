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

### Requirement: 可接受性的持久化取值遵循标准小写形式
依据 ISO/IEC 11179-3:2023 表 20（8.7.1.2），可接受性（Acceptability）的标准取值为小写：`preferred`、`admitted`、`deprecated`、`superseded`、`obsolete`。系统 MUST 在数据库中以该小写形式持久化可接受性字段（`designation.acceptability`、`definition.acceptability`、`designation_context_relation.acceptability`、`definition_context_relation.acceptability`、`designation_definition_pairing.acceptability`）。Java 枚举常量与前端、REST API 的表示可继续使用大写形式（`PREFERRED` 等），但持久化层 SHALL 通过映射在大写常量与标准小写存储值之间双向转换，读取时 MUST 大小写不敏感以兼容历史导入的小写数据。

#### Scenario: 落库为标准小写值
- **WHEN** 保存一条可接受性为 `PREFERRED` 的指称或定义
- **THEN** 数据库中该记录的 acceptability 列存储为小写 `preferred`

#### Scenario: 读取兼容大小写
- **WHEN** 数据库中存在 acceptability 为小写 `preferred`（标准格式）的历史导入记录
- **THEN** 系统 SHALL 正常读取并映射为枚举 `PREFERRED`，不抛出枚举解析异常


