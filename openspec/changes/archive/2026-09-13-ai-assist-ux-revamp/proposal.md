# 提案：AI 辅助交互重构（AI 生成）

## Why

已交付的 AI 协作能力（推断补全、划词解释、调试侧栏）在真实使用中暴露出结构性交互问题：

- 命名与语义不符：功能是「生成」，界面却叫「推断」。
- 权限模型含混：AI 使用能力把 SUPER_ADMIN 与 REGISTRAR 混同放行，未考虑「Admin 本身不是 Registrar」的情形。
- 触发入口与操作对象脱节：对话框级「AI 推断」按钮不对应具体字段；候选数量、Agent 选择推迟到运行时才做，打断工作流。
- 划词解释寄生在编辑表单内，形态受限；缺少「引用划词内容、继续追问」的对话形态。
- 调试侧栏快捷键硬编码 Ctrl+Shift+D；系统设置所有配置挤在单页。

## What Changes

- **更名**：界面文案「AI 推断」→「AI 生成」（仅 UI 层；代码标识 inference/assistant 不变）。
- **权限收紧**：AI 使用类端点（`/api/v1/inference/**`、`/api/v1/assistant/**` 全部方法）从 SUPER_ADMIN/REGISTRAR 收紧为**仅 REGISTRAR（业务角色）**；Agent 与 AI 辅助配置仍属 SUPER_ADMIN。SUPER_ADMIN 若不同时具备 Registrar 身份，不能使用 AI 功能。
- **系统设置子页化**：单页拆分为子路由子页（通用 / 数据源 / AI 辅助 / 数据迁移与危险区域）；新增「**AI 辅助**」子页集中承载：Agent 管理（既有卡片迁入）、生成偏好（候选数量）、快捷键设置。
- **快捷键可配置**：Codex 风格命令列表（命令 → 当前绑定 → 录制/重置），调试侧栏快捷键为首条命令；绑定持久化到服务端 `~/.metadata-registry/ai-assist.yml`。
- **浮动 AI 对话窗**：全局划词（只读文本）浮出「问 AI」→ 打开可拖拽浮动窗，划词内容作为引用上下文，可多轮追问（对话历史随请求重注入，后端仍单发作业模型）。新增 `selection-chat` 协作任务（TEXT 输出）。
- **字段级 AI 生成**：指称/定义编辑行右侧 ✨ 按钮 → 立即生成 → 浮动面板展示候选 → 点选回填该输入框。
- **运行时免配置**：移除运行时 Agent 切换与候选数选择——默认提供方、候选数量均在「AI 辅助」预设；移除对话框级「AI 推断」按钮与 InferenceDialog；移除编辑表单/详情页的 SelectionExplain 挂载与 ExplainPanel（由浮动对话窗取代），「存入注释」交互随之退役（explanatoryComment 编辑入口与 AiGenerationRecord 端点保留）。

## Impact

- 受影响代码：`web/assistant/**`、`web/inference/**`（任务、设置、SecurityConfig）、`metadata-registry-ui`（系统设置路由拆分、AppLayout、ItemFormDialog、新浮动窗/候选面板组件、i18n）。
- 受影响 spec：ai-governance-assistant（调试侧栏快捷键硬编码、划词解释交互、存入注释等需求被本变更修改，未归档直接同步）；ai-inference-agent-selection（运行时 Agent 切换语义变化）。
- 无新第三方依赖；无 schema/迁移变更。
