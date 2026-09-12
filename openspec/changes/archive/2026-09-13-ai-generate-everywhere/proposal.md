# 提案：字段级 AI 生成普及（含新建模式）

## Why

字段级「AI 生成」目前只在数据元**编辑**（已有 id）时可用：新建模式下按钮被门控隐藏，且 `complete-metadata` 任务强制要求 itemId（提示词只能取自库中已有条目）。但新建条目恰恰是元数据写作需求最强的场景；同时值域、数据元概念、值含义、数据类型等其它注册对象共用同一 ItemFormDialog，其指称/定义编辑行同样适用。需要把该能力普及为一项**通用编辑特性**，并作为架构原则沉淀。

## What Changes

- **门控放开**：ItemFormDialog 指称/定义编辑行的「AI 生成」按钮从「仅编辑态」放开为「新建与编辑均可用」（仍仅 Registrar）；凡使用该对话框的注册对象（数据元、值域、数据元概念、值含义、数据类型、数据类型方案、计量单位等）自动获得该能力。
- **草稿模式生成**：`complete-metadata` 任务输入规格放宽——`itemId` 可选；无 itemId 时接受 `params.draft`（条目类型 + 表单内已填的指称/定义行），提示词的「已知元数据」段由草稿内容组装；两者皆缺为参数错误。编辑态仍走 itemId 路径（库内数据为准）。
- **架构原则沉淀**：「凡可编辑指称/定义的界面 MUST 提供字段级 AI 生成」写入架构文档 `openspec/specs/system.md`（AI 辅助章节），作为后续新增编辑界面的默认要求。

## Capabilities

### New Capabilities

- `ai-generate-everywhere`: 字段级 AI 生成的通用性要求——新建/编辑全模式覆盖、草稿驱动的 complete-metadata、普及原则及其架构记录。

### Modified Capabilities

（无既有已归档 spec 被修改；AI 辅助相关行为规格均在未归档变更中维护，按需同步注记。）

## Impact

- 后端：`web/assistant/task/CompleteMetadataTask.java`（draft 参数与校验）、`InferencePromptBuilder`（草稿段落组装，复用写作规则与输出契约）；任务 inputSpec 变化（itemIdRequired: true→false，draft 互斥校验）。
- 前端：`ItemFormDialog.vue`（canAiGenerate 门控、新建模式下 draft 载荷组装）；共用该对话框的全部列表视图自动受益，无需逐页改动。
- 文档：`openspec/specs/system.md` 增加 AI 辅助架构原则。
- 无 API 路径/权限变化；无 schema 变化。
