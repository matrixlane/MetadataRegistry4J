# Current State

Use this file for the latest working context that the next session should read first.

## Active Focus

- Current task: 无进行中的开发任务。上一会话完成：AI 辅助数据治理六连变更（ai-metadata-inference → ai-generate-everywhere）+ enforce-valid-period 数据强制；全部功能已部署本机 8081。
- Why it matters now: **256 个工作区文件未 git commit**（六个半 OpenSpec 变更的全部代码、文档、测试与构建产物），丢失风险高，提交是最高优先级。

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
