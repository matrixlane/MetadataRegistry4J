# 任务：AI 辅助交互重构（AI 生成）

## 1. 后端

- [x] 1.1 权限收紧：SecurityConfig 对 `/api/v1/inference/**`、`/api/v1/assistant/**` 全部方法显式 `hasRole("REGISTRAR")`（置于通用规则前；含 generation-records、debug 两条既有显式规则改为仅 REGISTRAR）；配置类端点维持 SUPER_ADMIN；`GET /api/v1/assistant/settings` 限 REGISTRAR、`PUT` 限 SUPER_ADMIN
- [x] 1.2 AI 辅助设置：`AiAssistSettings`（version/generation.candidateCount 1-5 默认 3/hotkeys map）+ `AiAssistSettingsService`（SnakeYAML 读写 `~/.metadata-registry/ai-assist.yml`，损坏回退默认、保存即生效）+ 控制器 `GET/PUT /api/v1/assistant/settings`（PUT 校验候选数范围与快捷键格式）
- [x] 1.3 `selection-chat` 任务：selection.text 必填≤2000、params.question 必填、params.history 最近 10 轮注入、项目上下文摘要（有 itemId 时）+ 降级标注、TEXT 清洗
- [x] 1.4 候选数接入：complete-metadata 任务执行时从 AI 辅助设置读取 candidateCount 注入提示词（请求体不接受数量参数）
- [x] 1.5 后端单测：权限矩阵（非 Registrar 的 SUPER_ADMIN 403、Registrar 通过、配置端点 SUPER_ADMIN）、设置读写/损坏回退/校验、selection-chat 输入/提示词/历史截断/降级、候选数注入；inference/assistant 全包回归全绿

## 2. 前端

- [x] 2.1 系统设置子页化：`/system-settings` 拆为 general/datasource/ai-assist/danger 子路由 + 页内二级导航，重定向 general；路由仍限 ADMIN
- [x] 2.2 AI 辅助子页：Agent 管理卡片迁入、「生成偏好」候选数、「快捷键」命令列表（`aiDebug.toggle`，录制/恢复默认，GET 载入 + PUT 保存）
- [x] 2.3 auth store 拆分 Registrar 判定（`user.isRegistrar`），AI 入口全部改由该判定门控（含 AppLayout 调试快捷键监听，且监听按设置的绑定注册）
- [x] 2.4 浮动对话窗 `AiChatWindow` + 全局划词「问 AI」按钮（排除输入控件、≥2 字符）+ pinia 状态 + selection-chat 轮询收发 + 降级标注 + 复制
- [x] 2.5 字段级 AI 生成：ItemFormDialog 指称/定义编辑行右侧按钮 + `AiCandidatePopover`（自动生成/候选点选回填/取消/重试；无 Agent/数量控件）
- [x] 2.6 移除：对话框级「AI 推断」按钮与 InferenceDialog（含 spec）、SelectionExplain/ExplainPanel 及其挂载、存入注释前端流程；explanatoryComment 编辑入口保留
- [x] 2.7 更名与 i18n：界面「AI 推断」→「AI 生成」（zh/en，含系统设置卡片标题、按钮、提示）；新增组件词条中英文
- [x] 2.8 前端 vitest：快捷键录制/绑定注册、AI 辅助页、对话窗状态机（引用/多轮/轮询/降级）、字段级生成回填、权限门控；全量绿 + build 过

## 3. 收尾

- [x] 3.1 openspec 同步：ai-governance-assistant 未归档文档按 D7/快捷键可配置改写；本变更 validate --strict 通过
- [x] 3.2 GitNexus：编辑前 impact、收尾 detect_changes（无 partial/truncated）
- [x] 3.3 重打包 jar + 前端 dist 同步 static + 重启后端验证端点在线
