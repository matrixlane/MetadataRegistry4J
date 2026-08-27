## Why

所有 Item 子类（数据元、数据元概念、对象类、特性、值含义、计量单位、度量类别、数据类型、数据类型方案、概念域、值域、语境、约束集、命名规范、注册规范等）的注册区段 `ItemRegistrationSection.vue` 当前将"标识符生成方式"和"版本生成方式"分别作为一个独立的 `el-form-item` 行（单选按钮组 `el-radio-button`），与下方的"标识符" / "版本"输入框分离、各自占据一整行垂直空间。本质上这两个生成方式只是输入框的"模式"属性，并非与"标识符"、"版本"并列的独立字段。把它们作为独立行既不符合现代表单设计惯例（生成模式应作为字段复合控件的一部分），也浪费了垂直空间，并在视觉上把"标识符"+"生成方式"切成了两个分离的关注点。本变更把这两个生成方式控件下放到对应输入框的右侧，作为输入框的内嵌 UI 元素呈现，使"标识符/版本"字段成为"标识值 + 模式切换"的紧凑复合控件。

## What Changes

- 前端：移除 `ItemRegistrationSection.vue` 中"标识符生成方式"和"版本生成方式"两个独立的 `el-form-item` 行；将原 `el-radio-button` 控件改造为嵌入在 `el-input` `append` 槽位中的 inline 切换控件（如 `el-segmented` 或同等紧凑控件）。
- 显示规则：inline 切换控件仅在所选命名空间为 `AUTO_WITH_OVERRIDE` 模式且非编辑态时显示；`AUTO_ONLY` / `MANUAL_ONLY` 模式下 MUST 隐藏（无切换必要，由命名空间输入策略直接确定）。
- 行为保持等价（不改动 `identifierAutoInEffect` / `versionAutoInEffect` / `identifierChoice` / `versionChoice` 等既有逻辑）：
  - 自动模式：输入框禁用、占位符"保存后自动生成"、必填校验放行；段模板变量项照常展示。
  - 手动模式：输入框启用、强制非空校验、必要时按 `manualIdentifierPattern` 校验。
  - 编辑态：输入框禁用、占位符"留空则按命名空间策略自动递增"（版本）；inline 控件不显示。
- 后端：`ItemAdministrationService.ensureAdministeredItem`、`IdentifierInputPolicyService`、请求 DTO 等完全不变；纯前端 UI 布局调整，不改 API 负载格式、不改数据模型。
- 适用范围：所有使用 `ItemRegistrationSection` 的 Item 列表页（DataElement、DataElementConcept、ObjectClass、Property、ValueMeaning、MeasureClass、UnitOfMeasure、DataType、DataTypeScheme、ConceptualDomain、ValueDomain、Context、ConstraintSet、NamingConvention、RegistrySpecification 等）同步生效。
- 无障碍：inline 控件需支持键盘 Tab 聚焦、Enter/Space 切换、屏幕阅读器可读（通过 `aria-label` 等标准属性）。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities

- `identifier-auto-generation`：将"前端标识符生成模式切换与验证控制"中"前端项创建表单 MUST 提供"自动生成"与"手动输入"单选框"的要求由"独立 `el-radio-button` 表单行"调整为"输入框内嵌 inline 切换控件（与 `el-input` 视觉复合的 append 槽位组件）"；既有行为契约（自动禁用+占位符+校验放行、手动启用+强制非空、编辑态禁用）保持不变；同时补齐 `AUTO_ONLY` / `MANUAL_ONLY` 下隐藏 inline 控件与版本的 inline 控件等价行为。
- `identification`：将"所有 Item 子类界面提供统一的标识符/版本编辑区段"中"标识符输入（含自动/手工单选与段模板变量录入）、版本输入（含自动/手工单选）"调整为"标识符输入（含 inline 自动/手工切换控件与段模板变量录入）、版本输入（含 inline 自动/手工切换控件）"；明确生成方式不再以独立字段行呈现。

## Impact

- 前端：
  - `metadata-registry-ui/src/components/ItemRegistrationSection.vue`：移除两个独立的"标识符生成方式"、"版本生成方式" `el-form-item` 行；改为在 `el-input` 的 `append` 槽位承载 inline 切换控件；新增 `aria-label`、键盘焦点可达性。
  - `metadata-registry-ui/src/components/ItemRegistrationSection.spec.ts`：补 inline 控件场景测试（`AUTO_ONLY` / `MANUAL_ONLY` 隐藏切换控件、`AUTO_WITH_OVERRIDE` 显示并可切换、编辑态不显示、版本字段行为等价）。
  - `metadata-registry-ui/src/components/ItemFormDialog.spec.ts`：回归测试，确保数据元等列表页的注册区段行为不变。
- 后端：无变更。
- 数据模型 / 迁移脚本：无变更。
- API / 负载格式：无变更。
- 依据：ISO/IEC 11179-3:2023 第 7 章（Scoped Identifier）对 identified item 的统一刻画仍由 `identification` 能力覆盖；本次仅前端布局优化，不影响 11179 标准的语义约束。
