# Current State

Use this file for the latest working context that the next session should read first.

## Active Focus

- Current task: `registry-specification-full-editing`（注册规范属性完整编辑）——实现与自动化验证已完成（后端 RegistrySpecificationServiceTest + InitWizardServiceIntegrationTest 通过；前端 vitest 162/162、build 通过），**已按人工验收反馈追加**：初始化向导恢复注册规范配置步骤（表 28 全标量属性、默认值预填，6 步）。设置页回显字段名 bug（alternativeLanguageIds）已修。自动化验证全绿（后端 3 测试类、vitest 162/162、build、e2e 3/3）；**用户需重新构建前后端后重试验收**；可 `continue-archive` 归档。详见 session-journal/2026-09-17-registry-spec-full-editing.md。
- 同日前置：初始化向导已移除"注册规范"步骤（6→5 步，setup 自动创建默认规范，zh-CN），e2e/后端/构建全绿；openspec 主 spec 的初始化要求已同步改写。
- Current task: `add-profile-avatar`（个人资料头像上传 + 资料页横幅布局修复）——后端/前端实现与自动化验证已完成（1044 tests 0 failures；前端 build 通过），**待 4.3 人工验收**（姓名白区显示、上传/刷新保留、超限提示、恢复出厂清除）；随后代码评审（continue-review）与归档（continue-archive）。详见 session-journal/2026-09-17-profile-avatar.md。
- Current task: `add-startup-readiness-screen` 已于 09-17 经用户确认**被回滚**（09-15 未提交修改在 09-17 提交潮中被 `git restore`/`reset` 清掉），当日已**在本工作区原样重做**（后端探针 + SecurityConfig 放行 + 前端启动屏/延迟淡入/main.ts/vite es2022 + start.bat PowerShell Start-Process 日志方案 + OpenSpec artifacts + static/ 同步）；自动化验证全绿（后端 Startup*Test+InitWizard 回归、vitest 162/162、build、lint 0 error）。**待重新构建后端 jar + 人工验收后可归档**。详见 session-journal/2026-09-15-1141-startup-readiness-screen.md。
- 2026-09-15 晚：恢复出厂 500 修复（`ai_generation_record` 漏登记 `DatabaseEnvironment.EXT_TABLES`）已完成代码修复 + 守卫测试 + 文档补录 + OpenSpec 变更 `2026-09-15-ext-table-registry-sync`（已归档）；同晚追加结构性修复：`DatabaseCleaner` 按 JDBC 发现时 schema 限定表名（`TableRef`），根除无实体存量表（item_classification）寻错 schema 之类问题，后端已于 22:31 用新 jar 重启就绪，**待用户重试验证**。详见 session-journal/2026-09-15-2103-factory-reset-500-ext-tables.md。
- Why it matters now: **256+ 个工作区文件未 git commit**（六个半 OpenSpec 变更 + startup-readiness + 本次 factory-reset 修复的全部代码、文档、测试与构建产物），丢失风险高，提交是最高优先级。

## Latest Decisions

- Decision: Coding Agent 调用统一走 ACP（`acp-agent-protocol`），CLI 一次性模式废弃；`protocolVersion` 必须是 JSON number（字符串会被适配器 Invalid params 拒绝）。
- Reason: 常驻进程消除冷启动、结构化消息免 stdout 刮取、原生多轮；kimi 原生 `kimi acp`，codex/claude 经 `@agentclientprotocol/codex-acp`、`claude-agent-acp`。
- Decision: AI 使用仅 REGISTRAR（JWT registrar claim），Agent/AI 辅助配置仅 SUPER_ADMIN；非决定性选项前置「系统设置 → AI 辅助」。
- Decision: valid_period/datatype 基数约束落应用层校验（ValueDomainAssociationValidator + ConceptualDomainService），不改 DB 列可空性；既有 32 行已回填 2026-01-01 占位基准日。

## Open Questions

- Question: 六个半未归档变更何时归档（归档后 openspec/specs 才反映 AI 辅助与校验能力）？
- Blocker or follow-up: 无阻塞；milvus MCP 已注册 Kimi Code（上个会话修复），新会话可验证 `milvus_vector_search`。
- 遗留人工验收：真实 UI 全流程（新建值域有效期、字段级 AI 生成、划词对话窗、Ctrl+Shift+D 调试侧栏）。

## Next Recommended Step

- Next step: 按 OpenSpec 变更分组 git commit（提交前 `bunx gitnexus@latest detect-changes --scope all --repo .` 确认无 partial/truncated；排除 `_*.txt`、logs/、data/ 既有杂物；`metadata-registry-web/src/main/resources/static/` 构建产物随前端变更提交）。
- Suggested owner: matrixlane
