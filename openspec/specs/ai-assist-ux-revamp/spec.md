# ai-assist-ux-revamp Specification

## Purpose
TBD - created by archiving change ai-assist-ux-revamp. Update Purpose after archive.

## Requirements

### Requirement: AI 生成命名

界面文案 SHALL 使用「AI 生成」（en: AI Generate/Generation）取代「AI 推断」（仅 UI 层；代码标识、端点路径、i18n key 名不变）。

#### Scenario: 界面无「AI 推断」文案

- **WHEN** 用户浏览任何含 AI 功能的界面（系统设置、编辑对话框、按钮、提示）
- **THEN** 呈现的名称为「AI 生成」，不出现「AI 推断」

### Requirement: AI 使用权限仅限 REGISTRAR

AI 使用类端点（`/api/v1/inference/**`、`/api/v1/assistant/**` 全部方法）MUST 仅允许具备 Registrar 业务角色的用户访问；SUPER_ADMIN 若不具备 Registrar 身份 MUST 被拒绝（403）。配置类端点（`/api/v1/system/inference/agents**`、`PUT /api/v1/assistant/settings`）维持 SUPER_ADMIN；`GET /api/v1/assistant/settings` 限 REGISTRAR。前端 AI 入口（字段级生成按钮、问 AI 划词按钮、调试侧栏快捷键监听）MUST 仅对 Registrar 用户渲染/注册，不得以 SUPER_ADMIN 身份放行。

#### Scenario: 非 Registrar 的 Admin 使用 AI 被拒

- **WHEN** 不具备 Registrar 身份的 SUPER_ADMIN 调用 `/api/v1/assistant/jobs` 或 `/api/v1/assistant/debug/exchanges`
- **THEN** 系统返回无权访问错误，前端亦不对其渲染 AI 入口

#### Scenario: Registrar 身份的 Admin 正常使用

- **WHEN** 同时具备 Registrar 身份的 SUPER_ADMIN 使用 AI 功能
- **THEN** 系统正常响应

### Requirement: 系统设置子页化与 AI 辅助子页

系统设置 SHALL 按功能拆分为子页（通用、数据源、AI 辅助、危险区域），经页内二级导航切换。「AI 辅助」子页 SHALL 集中承载：Agent 管理（探测/启用/默认/自定义）、生成偏好（候选数量，1-5，默认 3）、快捷键设置（命令列表）。配置 SHALL 保存即生效。

#### Scenario: 子页导航

- **WHEN** ADMIN 进入系统设置
- **THEN** 可经二级导航在通用/数据源/AI 辅助/危险区域子页间切换，AI 辅助子页包含 Agent 管理、生成偏好与快捷键三区

### Requirement: 快捷键可配置

系统 SHALL 提供 Codex 风格快捷键设置：命令列表（命令标识、描述、当前绑定），支持按键录制与恢复默认；绑定持久化于服务端 `~/.metadata-registry/ai-assist.yml` 并经 `GET/PUT /api/v1/assistant/settings` 读写。调试侧栏开合命令（`aiDebug.toggle`，默认 Ctrl+Shift+D）为首条命令；前端 MUST 按当前绑定注册全局快捷键。非法绑定格式 MUST 被服务端拒绝。

#### Scenario: 修改调试侧栏快捷键

- **WHEN** SUPER_ADMIN 在 AI 辅助-快捷键中把 `aiDebug.toggle` 录制为 Ctrl+Alt+J 并保存
- **THEN** 设置持久化；Registrar 用户刷新后按 Ctrl+Alt+J 开合调试侧栏，原 Ctrl+Shift+D 不再触发

### Requirement: 浮动 AI 对话窗

系统 SHALL 提供浮动 AI 对话窗：Registrar 在只读文本上划选（≥2 字符，输入控件内不启用）后浮出「问 AI」按钮，点击打开可拖拽浮动窗，划词内容作为引用上下文；用户可在窗内就多轮追问，对话历史随每次请求重注入（服务端仅取最近 10 轮，不存会话）。每个问题 SHALL 以 `selection-chat` 协作任务异步执行（TEXT 输出，提供方为默认，不提供窗内 Agent 切换）；无关联项目时降级标注。消息以纯文本展示（禁 v-html）并支持复制。

#### Scenario: 划词引用并追问

- **WHEN** Registrar 划选某数据元详情中的一句文本，点击「问 AI」，在浮动窗中输入问题发送，收到回答后继续输入第二个相关问题
- **THEN** 第二次请求的对话历史中包含第一轮问答，回答结合引用原文与历史给出

#### Scenario: 非 Registrar 不出现问 AI

- **WHEN** 非 Registrar 用户划选文本
- **THEN** 不浮出「问 AI」按钮，无法打开对话窗

### Requirement: 字段级 AI 生成

编辑数据元（首批：ItemFormDialog 的指称/定义编辑行）时，字段右侧 SHALL 直接呈现「AI 生成」按钮（仅 Registrar、仅编辑态）。点击后立即以默认提供方与预设候选数创建生成作业（不再询问 Agent 与数量），并在锚定浮层中展示候选；用户点击某候选即回填对应输入框（多语行按行语言回填）。生成中可取消，失败可重试。对话框级「AI 推断」按钮与 InferenceDialog 运行时选择交互 MUST 移除。

#### Scenario: 定义字段一键生成并回填

- **WHEN** Registrar 编辑某数据元的定义，点击该字段右侧「AI 生成」按钮
- **THEN** 浮层进入加载态并自动开始生成；完成后展示预设数量的候选；用户点击其中一条，该定义输入框即填入该候选文本

#### Scenario: 运行时无配置项

- **WHEN** 字段级生成浮层打开
- **THEN** 浮层内不存在 Agent 选择与候选数量选择控件

### Requirement: 划词解释旧交互退役

编辑表单「已保存文本」预览区与详情页的划词解释挂载（SelectionExplain/ExplainPanel）及「存入注释」前端交互 MUST 移除（由浮动对话窗取代）；后端 `explain-selection` 任务、`AiGenerationRecord` 端点与 explanatoryComment 编辑入口保留。

#### Scenario: 编辑表单不再出现旧划词入口

- **WHEN** Registrar 打开数据元编辑对话框
- **THEN** 不出现「已保存文本（可划选解释）」预览区与解释面板；explanatoryComment 仍可在注册状态 tab 编辑

### Requirement: 工作流流畅性原则

凡非流程分支决定性的选项（Agent、候选数、快捷键等）MUST 前置到系统设置集中配置；运行时界面只呈现决定性选择（候选点选、提问内容）。后续涉及 AI 辅助的流程设计 SHALL 遵循该原则。

#### Scenario: 集中配置后工作流零分心

- **WHEN** 管理员已在 AI 辅助中配好默认 Agent、候选数与快捷键
- **THEN** Registrar 在字段级生成与对话窗的完整使用过程中无需做任何配置类选择
