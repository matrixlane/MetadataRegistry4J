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
- status: superseded（组2/3 部分，见 decision-2026-10-29-cv0430002-restore；组1/4/5 仍然 active）
- confidence: verified
- last_updated: 2026-10-29
- source: 用户 2026-10-12 指令；scratch/restore-test/code_recoding_proposal.md；scratch/ws-verify/report_duplicate_codes_errata.md
- owner: matrixlane
- review_after: 2027-04-12

Reason: WS/T 364-2023（2023-12-08 勘误后印刷版）仍有 4 组同码不同义（标准自身编号缺陷，非导入错误）。用户裁决不等标委会复审：CV03.00.203（加速器→4004）、CV05.01.016（多趾—右侧→1702）按提案更正码值；CV04.30.002（编码多处结构性缺陷，含 2 组同码双义及左腕关节整块 5 行部位字母错误）整域从基准脚本与数据库移除。由此基准数据**有意偏离标准原文**——若标准将来发布复审修订，须与本地处置对账（标准采纳值优先，本地差异按本决策回改）。

Alternatives considered: 维持原文双行等标委会（被用户否决）；CV04.30.002 仅改 2 个撞码（被否决——该域编码体系性问题，单点修改无法修复）。

Impact: 金库 data/metadata-registry.db、SQL Server metadata_registry、db-baseline 两脚本均已落地；CV04.30.002 唯一引用方 DE04.30.036.00 的 value_domain_id 置 NULL；回滚靠 2026-10-12 两份 .bak（metadata_registry-pre-adjudication / pre-errata）。

### Decision: CV04.30.002 按标准现行文本恢复在位（撤销 2026-10-12 组2/3 整域移除）
- id: decision-2026-10-29-cv0430002-restore
- type: decision
- status: active
- confidence: verified
- last_updated: 2026-10-29
- source: 用户 2026-10-29 指令（PDF↔DB 差异清单 4 项对账，"根据标准文档的最新修改更新本地数据与脚本"）
- owner: matrixlane
- review_after: 2027-04-12

Reason: 现行有效印刷版（2023-12-08 勘误版，本地 标准原文目录 副本元数据创建时间 2023-12-07）WS/T 364.8 第 4 页「常用放射诊断检查技术编码表」正文值域编号仍为 CV04.30.002；同次对账的 CV07.10.001/003/004 仅存在于第 13 部分前言修订说明（标准已自行改号 CV07.00.001/003/004，库内新编号在位，无需动作）。用户裁决：按标准现行文本整域恢复 CV04.30.002（值域+概念域+479 码值完整行图），DE04.30.036.00.value_domain_id 重挂该值域。标准未修复的 2 组重码（BP0KZZZZA、BQ2FZUZZZ）与左腕关节 5 行部位字母缺陷**随文收录**，不再本地移除；若标委会将来发布修复修订，按对账机制回改。

Alternatives considered: 维持整域移除（被用户 2026-10-29 指令撤销）；仅恢复域壳不含码值（被否决——对账目标是与标准正文一致）。

Impact: 行图自 scratch/ws-verify/metadata_registry-pre-errata-20261012.bak 精确恢复（抽取脚本 scratch/cv0430002-restore/extract_graph.py，id 集合与裁决脚本断言逐项吻合）；golden-new.db（新模型金库）已写入并生成新基准脚本（data/scripts/data-standards/ws/sqlserver/10-main.sql、20-derived.sql，行数声明同步更新）；SQL Server 活库应用同一行图（应用前备份 scratch/cv0430002-restore/metadata_registry-pre-cv0430002-restore-20261029.bak）。预置库 data/metadata-registry.db（golden-new 的 10-24 字节拷贝，表集合零差异）于 2026-10-29 按用户选项 2 自现行 golden-new 重新导出（旧文件备份 metadata-registry.db.bak-preregen-20261029），现与 golden/SQL Server/脚本四方口径一致（CV04.30.002 链 481、空壳 0、0 码值枚举值域 0、四处数据元重挂/解析齐全）。恢复后基准可预期地重新出现同码两义 2 组（errata 检测会再命中），属标准原文缺陷，非导入错误。守护测试 `AdministeredItemClosureConformanceTest` 不受影响（受管链闭环验证通过）。

### Decision: 枚举值域不允许空壳——KNOWN_GAP 5 壳整域删除
- id: decision-2026-10-29-no-empty-shells
- type: decision
- status: active
- confidence: verified
- last_updated: 2026-10-29
- source: 用户 2026-10-29 指令："枚举型值域删除就应该完全删除，不允许有所谓的'空壳'枚举值域，那没有意义"
- owner: matrixlane
- review_after: 2027-04-12

Reason: 2026-10-23 基线重建曾沿用"KNOWN_GAP 5 空壳 CV 保留在位"的处置（CV04.50.021/CV06.00.239/CV08.10.101/CV08.50.004/CV08.50.102，0 码值，CV08.50.102 标 OBSOLETE），理由是 363 有数据元引用它们。用户裁决该处置无意义：值域的"存在"以有码值为前提；被标准删除（364.16 前言声明删除 CV08.50.102）或从未发布代码表的空壳，不应以任何形式占位。引用悬空是标准自身的 dangling 引用，由引用方（数据元 value_domain_id 置 NULL）表达，不由壳承担。

Alternatives considered: 维持空壳在位（被用户否决）；保留壳但挂 OBSOLETE（即原处置，用户判定"没有意义"）。

Impact: 5 壳自 golden-new.db、重生成基准脚本（10-main/20-derived，KNOWN_GAP 头注行改写为删除说明）与 SQL Server 活库三处整域删除（含值域/概念域条目、受管链、标识、concept 行、指称、来源/提交关联、CV08.50.102 的 3 条溯源槽）；引用数据元 DE08.50.040.00/DE06.00.339.00/DE08.10.081.00/DE08.50.025.00/DE08.50.003.00 的 value_domain_id 置 NULL（DE04.30.036.00 先例）。活库应用前备份 scratch/cv0430002-restore/metadata_registry-pre-shell-purge-20261029.bak；删除脚本 ws-shells-purge-sqlserver.sql（守卫+断言+单事务）。执行后活库 0 码值枚举值域清点为 0；规模 value_domain 2156/conceptual_domain 2157/registered_item 11185/designation 20486 等全部命中新脚本声明。历史 315 个勘误误建空壳（值含义/概念域级，非值域级）不在本次范围；如需同口径清理可另行裁决。

### Decision: 悬空引用本地重挂（CV04.50.020 / CV09.00.403）
- id: decision-2026-10-29-dangling-relink
- type: decision
- status: active
- confidence: verified
- last_updated: 2026-10-29
- source: 用户 2026-10-29 指令："重挂 CV04.50.020 是正确的，可以应用"、"入院途径代码采用 WS364.17 的 CV09.00.403"
- owner: matrixlane
- review_after: 2027-04-12

Reason: 标准引用编号笔误/漂移的两例按组1/组4 先例本地更正：① 363.16 DE08.50.040.00（输血品种代码）允许值引用 CV04.50.021，364.9 实表为 CV04.50.020（28 码值，名称对应）；② 363.12 DE06.00.339.00（入院途径代码）引用 364.12 CV06.00.239（不存在），364.17 新增同主题表 CV09.00.403 入院途径代码表（4 码值）。用户裁决按实表重挂。与"空壳清除"决策配套：壳删除后引用方不留 NULL，而是挂到标准实际存在的正确表。

Alternatives considered: 保持 NULL（用户否决——实表明确存在且名称对应）；重挂后保留旧编号槽记录（否决——与组1/组4 一致，决策文档留痕即可）。

Impact: golden-new.db 两数据元 value_domain_id 重挂并重新生成脚本（data_element 段两行值变化）；活库补丁 relink_dangling_refs.sql（守卫+断言+单事务，幂等）已执行，应用前备份 metadata_registry-pre-relink-20261029.bak；API 实测两数据元均显示新值域。幽灵编号 CV04.50.021/CV06.00.239 保持 0 行。文档同步：ws-standard-defects-solutions-2026-10-29.md C2/D3 状态更新。
