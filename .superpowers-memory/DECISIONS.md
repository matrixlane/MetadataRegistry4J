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

### Decision: 非标准扩展字段一律 ext schema 卫星表，mdr 只装标准列
- id: decision-2026-10-07-mdr-ext-layering
- type: decision
- status: active
- confidence: verified
- last_updated: 2026-10-07
- source: openspec/changes/archive/2026-10-05-authority-contact-governance/（is_primary 误落 mdr 后迁出）；openspec/changes/move-ra-primary-to-ext/；AGENTS.md"数据模型分层规则"
- owner: matrixlane
- review_after: 2027-01-07

Reason: ISO/IEC 11179 标准表的可审计性要求 mdr schema 严格只含标准定义的表与列；应用扩展（如 Primary 标志、avatar、账号绑定）属软件自身语义，必须隔离在 ext schema 卫星表（1:1 按 id，不建 JPA 关联）。2026-10-05 authority-contact-governance 将 `is_primary` 直接加在 `mdr.registration_authority`（设计时错误类比 dimensionality.coordinate_indicator——后者实为 11179-31 表 22 标准属性），2026-10-07 用户指出后迁出至 `ext.registration_authority`。

Alternatives considered: 标准表加应用列（被否决——破坏标准层纯洁性，违反既往 remove-individual-nonstandard-fields/remove-registrar-identifier 判例）；应用层派生不设列（对"需持久化的隐式标志"不可行）。

Impact: 此后所有表结构设计/迁移评审第一项核对分层规则；AGENTS.md 已写入强制条款。

### Decision: WS/T 364-2023 同码不同义 4 组的本地处置（偏离标准原文，用户裁决）
- id: decision-2026-10-12-ws364-dup-code-adjudication
- type: decision
- status: active
- confidence: verified
- last_updated: 2026-10-12
- source: 用户 2026-10-12 指令；scratch/restore-test/code_recoding_proposal.md；scratch/ws-verify/report_duplicate_codes_errata.md
- owner: matrixlane
- review_after: 2027-04-12

Reason: WS/T 364-2023（2023-12-08 勘误后印刷版）仍有 4 组同码不同义（标准自身编号缺陷，非导入错误）。用户裁决不等标委会复审：CV03.00.203（加速器→4004）、CV05.01.016（多趾—右侧→1702）按提案更正码值；CV04.30.002（编码多处结构性缺陷，含 2 组同码双义及左腕关节整块 5 行部位字母错误）整域从基准脚本与数据库移除。由此基准数据**有意偏离标准原文**——若标准将来发布复审修订，须与本地处置对账（标准采纳值优先，本地差异按本决策回改）。

Alternatives considered: 维持原文双行等标委会（被用户否决）；CV04.30.002 仅改 2 个撞码（被否决——该域编码体系性问题，单点修改无法修复）。

Impact: 金库 data/metadata-registry.db、SQL Server metadata_registry、db-baseline 两脚本均已落地；CV04.30.002 唯一引用方 DE04.30.036.00 的 value_domain_id 置 NULL；回滚靠 2026-10-12 两份 .bak（metadata_registry-pre-adjudication / pre-errata）。
