## ADDED Requirements

### Requirement: DesignationDefinitionSection 复合编辑区
组件 SHALL 将指称编辑（`DesignationEditor`）、定义编辑（`DefinitionEditor`）、指称-定义配对编辑（`DesignationDefinitionPairingEditor`）三个部分集成到一个带折叠面板（`el-collapse`）的单一组件中，无需调用方手动组合三块代码。

组件 SHALL 接受以下 props：
- `designations: TextLineForm[]`（v-model:designations）
- `definitions: TextLineForm[]`（v-model:definitions）
- `pairings: PairingForm[]`（v-model:pairings）
- `showPairings?: boolean`（默认 `true`）：是否显示配对面板
- `itemId?: string`：当有 itemId 时表示编辑模式，配对面板对应激活；无 itemId 时配对面板 SHALL 显示提示"保存后方可配置配对"

组件 SHALL 通过三个独立的 `update:designations`、`update:definitions`、`update:pairings` 事件向父组件通知各自变更。

三个面板 SHALL 使用 `el-divider` 或 `el-collapse` 分隔，面板标题使用语义化标签："指称（Designations）"、"定义（Definitions）"、"指称-定义配对（Pairings）"。

#### Scenario: 基础渲染三个面板
- **WHEN** 组件以默认 props 挂载
- **THEN** 页面 SHALL 显示指称、定义、配对三个面板，各面板 SHALL 包含对应子组件

#### Scenario: 隐藏配对面板
- **WHEN** 父组件传入 `showPairings=false`
- **THEN** 配对面板 SHALL 不渲染（`v-if` 而非 `v-show`）

#### Scenario: 无 itemId 时配对面板提示
- **WHEN** 组件挂载时未传入 `itemId`（或 itemId 为空字符串）
- **THEN** 配对面板内容区 SHALL 显示"保存后方可配置配对"占位提示，而非空白编辑区

#### Scenario: 指称变更向上传递
- **WHEN** 用户在指称面板中添加一条新指称
- **THEN** 组件 SHALL 触发 `update:designations` 事件，携带更新后的完整 `TextLineForm[]` 数组

#### Scenario: 定义变更向上传递
- **WHEN** 用户在定义面板中删除一条定义
- **THEN** 组件 SHALL 触发 `update:definitions` 事件，携带移除后的完整 `TextLineForm[]` 数组

### Requirement: DesignationDefinitionSection 被所有简单场景调用方使用
`NamespaceList.vue`、`ConceptualDomainList.vue`、`ValueDomainList.vue` 等不需要 tab 结构的视图，在其内联表单对话框中 SHALL 使用 `DesignationDefinitionSection` 替代原有分散的 `MultilingualTextEditor` 调用。

#### Scenario: NamespaceList 使用复合组件
- **WHEN** 注册管理员打开命名空间创建对话框
- **THEN** 对话框内 SHALL 显示 `DesignationDefinitionSection`，行为与原有 MultilingualTextEditor 完全等同

#### Scenario: ItemFormDialog 不强制使用 DesignationDefinitionSection
- **WHEN** `ItemFormDialog` 需要独立控制各 tab 的验证状态
- **THEN** `ItemFormDialog` MAY 继续直接使用 `DesignationEditor`、`DefinitionEditor`、`DesignationDefinitionPairingEditor` 三个子组件，而非通过 `DesignationDefinitionSection`
