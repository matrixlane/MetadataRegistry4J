# Decisions

Use this file for important project or workflow decisions that should remain visible across sessions.

## Entry Template

```md
### Decision: <short-title>
- id: decision-YYYY-MM-DD-<slug>
- type: decision
- status: active
- confidence: verified
- last_updated: YYYY-MM-DD
- source:
- owner:
- review_after:

Reason:

Alternatives considered:

Impact:
```

## Notes

- Put only decisions that still matter to future sessions.
- Move outdated decisions to `status: superseded` instead of deleting history blindly.
- Reference code, docs, tests, or session notes when possible.
- Durable entries should always include `id`, `status`, `confidence`, `source`, `last_updated`, and `review_after`.
- Do not mark an entry `confidence: verified` unless `source` points to real evidence.

### Decision: Coding Agent 调用统一走 ACP，CLI 一次性模式废弃
- id: decision-2026-09-13-acp-only
- type: decision
- status: active
- confidence: verified
- last_updated: 2026-09-13
- source: openspec/changes/acp-agent-protocol/；本机三 Agent initialize 握手与 PINEAPPLE 端到端实测
- owner: matrixlane
- review_after: 2026-12-13

Reason: 常驻进程摊销冷启动、JSON-RPC 结构化消息免除 stdout 刮取（曾发生提示词回显抓出占位候选事故）、原生多轮与取消。

Alternatives considered: 保留 CLI 模式作回退（用户明确否决：CLI 方式整体废弃）；直连模型 API（另行立项，不混入本地 Agent 配置）。

Impact: `CliAgentProvider` 已删；内置命令 claude-agent-acp / codex-acp / kimi acp；探测=initialize 握手（10s）；设置文件 inference-agents.yml v2（旧 CLI args 覆盖被丢弃）。ACP `protocolVersion` 必须为 JSON number。

### Decision: ISO 11179-31 基数约束落应用层校验，不改 DB 列可空性
- id: decision-2026-09-13-valid-period-app-layer
- type: decision
- status: active
- confidence: verified
- last_updated: 2026-09-13
- source: openspec/changes/enforce-valid-period/；标准 PDF 表 5/表 2 原文；data/_backfill_valid_period.sql
- owner: matrixlane
- review_after: 2026-12-13

Reason: 值域 datatype、值域/概念域 valid_period.start 均为基数 1；三方言 ddl-auto 对既有列空性变更行为不一致，应用层校验可带标准条款错误消息且可测试。

Alternatives considered: DB 列改 NOT NULL（被否决，方言风险）；前端必填+预填当日作为 UX 层配合（保留）。

Impact: 保存前缺 start 即 422 拒绝；既有 32 行回填 2026-01-01 占位值（RA 可修）；`MetadataObjectAssociationException` 已映射为参数校验错误（此前落 500）。
