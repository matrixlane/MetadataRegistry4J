# 设计：AI 辅助交互重构（AI 生成）

## Context

已交付（未归档）：`ai-metadata-inference`（补全）、`ai-inference-agent-selection`（Agent 注册表/探测/设置/用时切换）、`ai-governance-assistant`（任务框架、划词解释、生成记录、调试侧栏）。本次不改接入层与作业内核，重构的是**交互层与权限边界**：谁可用、在哪触发、何时做选择。

## Decisions

### D1：命名——界面一律「AI 生成」

zh-CN 文案「AI 推断」→「AI 生成」，en-US 对应 "AI Generate/Generation"。代码标识（包名 inference、类名、端点路径、i18n key 名）不变，避免无意义 churn；i18n 只改值不改 key（新组件用新 key 除外）。

### D2：权限模型——使用=REGISTRAR（业务角色），配置=SUPER_ADMIN

- 使用类：`/api/v1/inference/**`、`/api/v1/assistant/**` **全部方法**在 SecurityConfig 显式 `hasRole("REGISTRAR")`，置于通用规则之前。`ROLE_REGISTRAR` 来自用户 `isRegistrar` 业务标志——SUPER_ADMIN 若不具备 Registrar 身份即被 403。
- 配置类：`/api/v1/system/inference/agents**`（Agent 管理）与 `/api/v1/assistant/settings` 的 **PUT**（AI 辅助设置）维持 SUPER_ADMIN。
- `GET /api/v1/assistant/settings` 限 REGISTRAR（注册人员需读取候选数与快捷键以驱动前端行为）。
- 前端：`auth.isRegistrar` 不再把 SUPER_ADMIN 等同于 Registrar——拆分出「是否 Registrar 用户」（`user.isRegistrar`）用于 AI 入口门控；菜单/路由的 admin 组逻辑不变。调试 exchanges 的「SUPER_ADMIN 全览」语义保留在服务端（仅当其同时为 Registrar 才能到达端点）。
- 既有 `generation-records`、`debug` 两条显式规则同步由 hasAnyRole(SUPER_ADMIN,REGISTRAR) 改为 hasRole(REGISTRAR)。

### D3：AI 辅助设置存储与端点

新 YAML `~/.metadata-registry/ai-assist.yml`（SnakeYAML，模式同 InferenceAgentSettingsService；损坏回退默认、保存即生效）：

```yaml
version: '1'
generation:
  candidateCount: 3        # 1..5，默认 3
hotkeys:
  aiDebug.toggle: Ctrl+Shift+D
```

端点：`GET /api/v1/assistant/settings`（REGISTRAR 读）与 `PUT /api/v1/assistant/settings`（SUPER_ADMIN 写，校验候选数范围与快捷键格式）。默认提供方维持 `inference-agents.yml` 的 `defaultProvider`（Agent 卡片既有交互），不重复存储。complete-metadata 任务执行时从设置读取候选数注入提示词；请求体不再接受候选数/数量参数。

快捷键绑定格式：`Ctrl+Shift+D`（修饰键 Ctrl/Alt/Shift/Meta 有序排列 + 主键字母/数字/功能键）；服务端做格式校验，非法拒绝。

### D4：系统设置子页化

`/system-settings` 改为布局 + 子路由：页内左侧二级导航（el-menu），子页：

| 路由 | 内容 |
|---|---|
| `/system-settings/general` | 通用配置（新用户审核、允许注册） |
| `/system-settings/datasource` | 数据源 + 数据迁移 |
| `/system-settings/ai-assist` | **AI 辅助**（见下） |
| `/system-settings/danger` | 危险区域（恢复出厂设置） |

`/system-settings` 重定向 general；路由与菜单仍限 ADMIN。AI 辅助子页内三区：「Agent 管理」（既有探测/启用/默认/自定义卡片迁入）、「生成偏好」（候选数量 1-5）、「快捷键」（Codex 风格命令列表：命令 id/描述/当前绑定/录制按键/恢复默认——首条命令 `aiDebug.toggle`「弹出 AI 对话调试侧边栏」）。

### D5：浮动 AI 对话窗 + `selection-chat` 任务

- 新协作任务 `selection-chat`（outputKind=TEXT，inputSpec：itemId 可选、selection REQUIRED）：输入 `selection.text`（必填≤2000）、`params.question`（必填）、`params.history`（可选，[{question,answer}]，服务端仅取最近 10 轮注入）；提示词 = 项目上下文摘要（有 itemId 时，复用 InferencePromptBuilder 抽出的上下文段）+ 引用原文 + 历史问答 + 新问题；无 itemId 降级并标注（同 explain-selection 惯例）。
- 前端全局组件 `AiChatWindow`（AppLayout 挂载，仅 Registrar 渲染）：fixed 定位卡片（约 400px，标题栏可拖拽、可关闭）；内容：引用块（划词原文，可移除重选）、消息列表（user/assistant 气泡，`<pre>` 纯文本）、输入框 + 发送（Enter 发送，生成中禁用并可取消）。状态存 pinia（messages、quote、open、pendingJobId）。
- 全局划词监听（AppLayout，仅 Registrar）：mouseup 时若选区非折叠、长度≥2 且不在 input/textarea/contenteditable 内 → 选区旁浮出小按钮「✨ 问 AI」→ 点击把选段写入 quote 并开窗（周边文本取所在元素 textContent 截断）。编辑输入框内依旧不启用。
- 发送即创建 `selection-chat` 作业并轮询，完成追加 assistant 消息；失败消息内联显示 + 可重试。多轮=客户端保存历史随 params 重注入，**服务端不存会话**（单发作业模型不变）。
- 对话窗不提供 Agent 切换（用默认提供方）；要换 Agent 去系统设置改默认。提供「复制」消息按钮。

### D6：字段级 AI 生成

- ItemFormDialog 的指称/定义编辑行（MultilingualTextEditor 行或其后继结构）右侧渲染「✨ AI 生成」小按钮（仅 Registrar、仅编辑态）。
- 点击：以 `params.fields=[该字段 kind]` 创建 complete-metadata 作业（提供方=默认、候选数=设置值，均不询问）→ 锚定按钮的浮层 `AiCandidatePopover`：加载态（可取消）→ 候选列表（纯文本、转义）→ **点击候选即回填该输入框**（多语行按行语言回填 designation/definition 文本）；失败态显示错误 + 重试。浮层内**没有** Agent 选择与数量选择。
- 移除：ItemFormDialog 对话框级「AI 推断」按钮、`InferenceDialog.vue` 及其 spec（运行时 Agent 切换/历史/候选数交互整体退役）。`/api/v1/inference/**` 后端端点保留（兼容外观）。

### D7：划词解释旧交互退役

- 移除 `SelectionExplain.vue`/`ExplainPanel.vue` 及其在 ItemFormDialog「已保存文本」预览区、MetadataDetailView 的挂载；「存入注释」交互随之移除（回填 explanatoryComment + 双轨写生成记录的前端流程删除）。
- 后端保留：`explain-selection` 任务（API 能力）、`AiGenerationRecord` 实体与端点（后续任务的来源记录写路径）、explanatoryComment 编辑入口（注册状态 tab）。
- ai-governance-assistant 变更（未归档）相应需求同步改写：划词解释交互替换为浮动对话窗、存入注释退役、调试快捷键可配置。

### D8：交互设计原则（沉淀，约束后续流程设计）

**工作流流畅性优先**：凡非流程分支决定性的选项（用哪个 Agent、生成几个候选、快捷键绑定），一律前置到系统设置集中配置；运行时界面只呈现决定性选择（选哪条候选、问什么问题）。后续涉及 AI 辅助或其它多步流程的设计 MUST 遵循此原则。

## Risks / Trade-offs

- [全局划词监听误触] → 仅 Registrar、排除输入控件、选区≥2 字符才浮按钮；鼠标按下即收起。
- [历史重注入放大提示词] → 服务端截断最近 10 轮；selection/surroundingText 既有截断规则沿用。
- [权限收紧影响既有 admin 体验] → admin 若无 Registrar 身份将看到 AI 入口消失，符合需求 #2 语义；config 能力不受影响。
- [多轮对话真实性] → 无服务端会话，超长历史被截断时模型表现下降，属可接受取舍（Non-Goal 仍是持久化会话）。
- [设置子页化改动面] → 仅前端路由/组件拆分，后端零改动。

## Migration Plan

1. 后端：权限收紧 + AI 辅助设置（YAML/端点）+ selection-chat 任务 + 候选数注入。
2. 前端：系统设置子页化 + AI 辅助页（Agent 迁入/生成偏好/快捷键）。
3. 前端：浮动对话窗 + 全局划词 + 字段级生成 + 旧交互移除 + 更名。
4. 回归：后端 inference/assistant 全绿；前端 vitest + build；openspec 同步。

回滚：阶段 1 独立于 UI；2-3 为前端增量可整体回退。


## 变更记录（2026-09：acp-agent-protocol）

本地 Coding Agent 调用方式由「CLI 一次性调用」（`--version` 探测、`{prompt}` 占位符、stdout 文本刮取）整体切换为 **ACP 协议**（JSON-RPC 2.0 over stdio/NDJSON，每 Agent 常驻进程懒启动 + initialize 握手，每作业 session/new→prompt→累积 agent_message_chunk→close）。

## 变更记录（2026-09：ai-generate-everywhere）

字段级 AI 生成由「仅数据元编辑态」普及为「全部共用 ItemFormDialog 的注册对象、新建与编辑全模式」（门控从 isRegistrarUser && isEditing 放开为仅 isRegistrarUser）；complete-metadata 的 itemId 改为可选，新建模式以 `params.draft`（表单已填内容）组装提示词（itemId 优先、空草稿拒绝）。普及原则已写入架构文档 openspec/specs/system.md「AI 辅助」小节。
