## 1. 新增原子组件：AcceptabilitySelect

- [x] 1.1 新建 `src/components/AcceptabilitySelect.vue`，实现五枚举值的 el-select，支持 `v-model`、`size`、`clearable`、`placeholder` props
- [x] 1.2 验证：在浏览器中确认五个选项中文标签正确显示，v-model 双向绑定正常工作

## 2. 新增原子组件：ContextSelector

- [x] 2.1 新建 `src/components/ContextSelector.vue`，实现远程搜索的 el-select，调用 `contextApi.options({ q })`，支持 `v-model`、`clearable`、`size`、`placeholder` props
- [x] 2.2 在 `onMounted` 中以空字符串调用一次 `contextApi.options` 预加载初始选项列表
- [x] 2.3 验证：在浏览器中确认挂载时已有初始选项，输入关键词可触发远程搜索，清除后 v-model 值为 undefined

## 3. 重构 MultilingualTextEditor → DesignationEditor

- [x] 3.1 新建 `src/components/DesignationEditor.vue`，复用 MultilingualTextEditor 的模板结构，语言选择器使用单行 `el-input`（非 textarea），上下文区域改用 `ContextSelector` + `AcceptabilitySelect` 子组件
- [x] 3.2 组件标签统一使用"指称"语义：添加按钮"添加指称"，占位符"输入指称文本"
- [x] 3.3 确保 `update:modelValue` 事件携带完整新数组（不可变更新），不直接修改 prop
- [x] 3.4 验证：添加/删除指称、展开/折叠上下文区、添加/删除上下文关联均正常工作

## 4. 新增 DefinitionEditor

- [x] 4.1 新建 `src/components/DefinitionEditor.vue`，结构与 DesignationEditor 相同，但文本输入使用 `type="textarea"` 的 el-input（支持多行）
- [x] 4.2 组件标签统一使用"定义"语义：添加按钮"添加定义"，占位符"输入定义文本"
- [x] 4.3 上下文区域同样使用 `ContextSelector` + `AcceptabilitySelect` 子组件
- [x] 4.4 验证：定义文本框支持多行回车，其他行为与 DesignationEditor 一致

## 5. 重构 DesignationDefinitionPairingEditor

- [x] 5.1 修改 `src/components/DesignationDefinitionPairingEditor.vue`，将内联的 contextId el-select 替换为 `<ContextSelector>`，将内联 of acceptability el-select 替换为 `<AcceptabilitySelect>`
- [x] 5.2 验证：配对编辑器的上下文选择与可接受性选择样式与其他编辑器一致

## 6. 新增复合组件：DesignationDefinitionSection

- [x] 6.1 新建 `src/components/DesignationDefinitionSection.vue`，接受 `v-model:designations`、`v-model:definitions`、`v-model:pairings`、`showPairings`、`itemId` props
- [x] 6.2 使用 el-divider 分隔三个区域，面板标题分别为"指称（Designations）"、"定义（Definitions）"、"指称-定义配对（Pairings）"
- [x] 6.3 当 `itemId` 为空时，配对区显示"保存后方可配置配对"占位提示
- [x] 6.4 当 `showPairings` 为 false 时，`v-if` 移除配对区
- [x] 6.5 验证：三个 v-model 事件各自独立触发，配对区的 itemId 条件显示正确

## 7. 更新 ItemFormDialog.vue

- [x] 7.1 将 `import MultilingualTextEditor` 替换为分别 import `DesignationEditor` 和 `DefinitionEditor`
- [x] 7.2 在"指称" tab 中用 `<DesignationEditor>` 替换 `<MultilingualTextEditor label="指称">`
- [x] 7.3 在"定义" tab 中用 `<DefinitionEditor>` 替换 `<MultilingualTextEditor label="定义">`
- [x] 7.4 在"指称-定义配对" tab 中确认 `DesignationDefinitionPairingEditor` 已使用重构后的子组件（步骤 5 完成后自动生效）
- [x] 7.5 验证：ItemFormDialog 中各 tab 的编辑功能、表单校验、tab 错误标记均正常工作

## 8. 更新简单场景调用方

- [x] 8.1 修改 `src/views/namespace/NamespaceList.vue`：移除两处 `<MultilingualTextEditor>`，改用 `<DesignationDefinitionSection v-model:designations="form.designations" v-model:definitions="form.definitions" :showPairings="false" />`
- [x] 8.2 修改 `src/views/conceptual-domain/ConceptualDomainList.vue`：同上，替换 MultilingualTextEditor
- [x] 8.3 修改 `src/views/value-domain/ValueDomainList.vue`：同上，替换 MultilingualTextEditor
- [x] 8.4 使用 grep 确认代码库中不再有 `MultilingualTextEditor` 的引用（彻底迁移完成）
- [x] 8.5 验证：三个视图的创建/编辑对话框功能与迁移前行为完全一致

## 9. 清理旧组件

- [x] 9.1 确认 `MultilingualTextEditor.vue` 已无任何引用后，将其删除（或保留为内部实现但不导出）
- [x] 9.2 运行 `npm run build` 确认无 TypeScript 编译错误、无未解析的 import
- [x] 9.3 运行 Playwright E2E 测试（`npx playwright test`），确认命名空间页面及其他相关页面功能正常
