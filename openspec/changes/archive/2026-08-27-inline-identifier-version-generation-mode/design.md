## Context

`ItemRegistrationSection.vue` 当前布局（自上而下）：

1. 命名空间下拉
2. 标识符生成方式（`el-radio-button` 整行：`自动生成` / `手动输入`）
3. 标识符输入框
4. 段模板变量项（命名空间策略为段模板时按变量段渲染）
5. 版本生成方式（`el-radio-button` 整行：`自动生成` / `手动输入`）
6. 版本输入框
7. 注册机构行

两个生成方式控件各自占用独立 `el-form-item` 行与一行高度。但本质只是"标识符" / "版本"输入框的"模式"属性，与输入框强绑定：`identifierAutoInEffect` / `versionAutoInEffect` 已经综合了 `props.isEditing`、命名空间 `identifierInputMode` / `versionInputMode`、`identifierChoice` / `versionChoice` 三个因素决定输入框的禁用与占位符。

改动目标：把"标识符生成方式"和"版本生成方式"控件内嵌到对应 `el-input` 的 `append` 槽位，使"标识符" / "版本"字段成为"标识值 + 模式切换"的紧凑复合控件；其余布局与逻辑完全保留。

约束：

- 不改动 `props` / `defineExpose` / 事件签名，保持宿主 `ItemFormDialog` 不感知本次变更。
- 不引入新依赖；Element Plus 已提供 `el-segmented`（2.6.0+）与既有 `el-radio-button` 两种紧凑选项。
- 不改后端 API、不改请求 DTO、不改 `ItemAdministrationService.ensureAdministeredItem` 路径。
- 跨所有 Item 列表页行为同步生效（DataElement、DataElementConcept、ObjectClass、Property、ValueMeaning、MeasureClass、UnitOfMeasure、DataType、DataTypeScheme、ConceptualDomain、ValueDomain、Context、ConstraintSet、NamingConvention、RegistrySpecification 等）。

## Goals / Non-Goals

**Goals:**

- 生成方式控件在 UI 上作为标识符 / 版本输入框的一部分呈现，不再独立成行。
- 控件行为与现状等价：AUTO_ONLY / MANUAL_ONLY 命名空间下隐藏切换控件；AUTO_WITH_OVERRIDE 下显示切换控件；编辑态不显示切换控件（标识符仍禁用）。
- 满足无障碍要求：控件可通过键盘 Tab 聚焦、Enter/Space 切换、屏幕阅读器可读。
- 所有使用 `ItemRegistrationSection` 的 Item 列表页（数据元等）行为同步生效。
- props/事件签名保持向后兼容；既有 `ItemFormDialog.spec.ts` 不需要改动预期行为。

**Non-Goals:**

- 不改后端 API、不改负载格式、不改数据模型。
- 不改段模板变量项的展示位置与布局。
- 不改注册机构行、命名空间行的展示方式。
- 不改 `identifierAutoInEffect` / `versionAutoInEffect` / `identifierChoice` / `versionChoice` / `templateVariables` / `variableValues` 等既有状态。
- 不重写 `ItemRegistrationSection` 的整体结构，仅调整模板中两个 `el-form-item` 行的形态。

## Decisions

### 控件选型：`el-segmented`（推荐） vs `el-select`

首选 `el-segmented`：

- 紧凑（一行两个短标签："自动" / "手动"），与 `el-input` `append` 槽位等高，符合表单"输入 + 模式切换"复合控件惯例。
- 原生键盘可达：Tab 聚焦、方向键切换选项、Enter/Space 触发。
- Element Plus 2.6.0+ 内置，零额外依赖。
- 与原 `el-radio-button` 的"单选切换"语义保持一致，迁移对用户心智模型无冲击。

备选 `el-select`（下拉）：

- 占用宽度更窄，但切换需二次点击；表单场景下切换频次低可接受。
- 视觉上"输入框 + 下拉"更像两个独立控件，不如 segmented 一体感强。

最终选择：在 tasks 阶段做最小可行版时倾向 `el-segmented`，高度与 `el-input` 对齐；若出现视觉对齐问题回退至 `el-radio-button` 内联小尺寸版（仍是 Element Plus 内置）。

### 槽位选择：`el-input` 的 `append` 槽位

- `append` 在 Element Plus 中与 input 同高度，提供独立的可点击/可聚焦区域；与"搜索框 + 按钮"等常见模式一致，符合用户对"输入 + 模式切换"复合控件的视觉预期。
- `prefix` / `suffix` 仅承载图标或单段文本，不适合承载交互控件。
- 当命名空间 `identifierInputMode` 为 `AUTO_ONLY` / `MANUAL_ONLY` 或编辑态时，整个 `append` 槽位 `v-if` 为 false，输入框恢复为纯输入态。

### 隐藏与启用逻辑：复用既有 `showIdentifierChoice` / `showVersionChoice`

- 计算属性 `showIdentifierChoice = !props.isEditing && identifierMode === 'AUTO_WITH_OVERRIDE'` 已存在；改为直接驱动 `append` 槽位 `v-if`。
- 版本字段同理复用 `showVersionChoice`。
- 编辑态下两控件均 `v-if` 隐藏。

### 字段 label 处理

- 原模板"标识符生成方式" `el-form-item` 的 label 移除（控件已内嵌，不再需要外置 label）。
- "标识符" / "版本" 输入框 label 不变（仍是 `el-form-item label="标识符"` / `label="版本"`），其下 `prop="registration.identifier"` / `prop="registration.version"` 校验规则不变。
- 段模板变量项的位置不动（仍紧跟"标识符"输入框之后），变量项内嵌于 inline 控件之下不影响。

### 无障碍

- 在 inline 控件外层加 `<div role="radiogroup" aria-label="标识符生成方式">` / `aria-label="版本生成方式"`。
- `el-segmented` 默认支持键盘导航；如最终回退到 `el-radio-button`，则按 radio group 语义标注 `role="radio"` / `aria-checked`。
- 控件隐藏时（`v-if=false`）整组从 DOM 移除，无需特殊 `aria-hidden`。

### 主题 / 样式

- inline 控件不引入新的全局样式；沿用 Element Plus 现有组件 token。
- 在 `<style scoped>` 中为 `.item-registration-section .el-input-group__append` 提供少量 padding 微调，避免与原 el-radio-button 行的 padding 不一致。

## Risks / Trade-offs

- [inline 控件宽度挤压标识符输入区域] → `el-input` 仍占满父容器；append 宽度由 segmented 自身尺寸决定（"自动" / "手动" 两段约 110px），不影响输入区。响应式断点（< 768px）下 append 仍保留，但控件字号略小，不退化。
- [el-segmented 在某些 Element Plus 版本下未注册] → 验证当前 `package.json` 中 Element Plus 版本 ≥ 2.6.0；若不满足，tasks 阶段回退到 `el-radio-button` 内联小尺寸版。
- [其他 Item 列表页未独立测试] → `ItemRegistrationSection` 是共享组件；覆盖其 spec 即可，`ItemFormDialog.spec.ts` 做冒烟回归。
- [老用户习惯独立成行的生成方式控件] → 内嵌控件切换频次极低（一般首次选择 AUTO 即可），UX 提升大于迁移成本；本次无配置项迁移。
- [props/事件签名变更触发宿主 regression] → 严格保持 `defineProps` / `defineEmits` / `defineExpose` 不变，仅调整模板两个 `el-form-item` 的形态。

## Migration Plan

纯前端 UI 变更，无数据迁移。

- 部署：合并 `metadata-registry-ui` 前端构建产物，刷新页面即生效。
- 回滚：还原 `ItemRegistrationSection.vue` 模板（保留 script 逻辑即可），无需数据库操作。
- 验证：`npx vitest run` 覆盖 `ItemRegistrationSection.spec.ts` 与 `ItemFormDialog.spec.ts`；`npx vite build` 通过。

## Open Questions

1. 控件视觉最终样式：`el-segmented` vs `el-radio-button` 小尺寸内联版？倾向 `el-segmented`，等待 tasks 阶段最终定稿。
2. 是否在窄屏（< 768px）下退化为图标按钮 + popover？本次先按桌面端优先实现，窄屏处理留作后续增强（如有需要）。
3. 是否同步调整注册区段的整体栅格密度（如减少 `el-form-item` 之间的垂直间距）？本次不动，留作后续布局优化。
