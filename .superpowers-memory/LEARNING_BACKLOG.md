# Learning Backlog

Use this file to track reusable lessons that may deserve a future workflow, skill, checklist, or project rule.

## Candidate Format

```md
### Candidate: <short-name>
- candidate_id: learn-YYYY-MM-DD-<slug>
- type: learning_candidate
- status: proposed
- confidence: inferred
- last_updated: YYYY-MM-DD
- source:
- owner:
- review_after:
- trigger:
- repeated_pattern:
- impact:
- evidence_count:
- repeated_times:
- suggested_artifact:
- promote_decision:
- linked_entries:
```

## Promotion Guidance

- Keep a candidate in `proposed` until it shows a repeated pattern or clear cross-session value.
- Move it to `ready_for_promotion` when it has enough evidence and a clear target artifact.
- Use `superseded` when the lesson is no longer relevant or has already been turned into a durable artifact.
- Promotion-ready candidates should normally have `evidence_count >= 2`, `repeated_times >= 2`, `source`, `review_after`, and linked evidence.

## Notes

- Put durable project facts in `PROJECT_CONTEXT.md`, not here.
- Put current task state in `CURRENT_STATE.md`, not here.
- Put one-off session summaries in `session-journal/`, not here.
- Use this file only for lessons that look reusable across future sessions.

### Candidate: MCP 缺失先修复再降级
- candidate_id: learn-2026-09-13-mcp-repair-first
- type: learning_candidate
- status: superseded
- confidence: verified
- last_updated: 2026-09-13
- source: 2026-09-13 会话（用户指示记录此经验）
- owner: matrixlane
- review_after: 2026-12-13
- trigger: 当前会话中 MCP 工具（gitnexus/milvus 等）缺失
- repeated_pattern: 用户报告"又不行了"；gitnexus 启动错误在日志中持续多日
- impact: 避免无谓降级到 CLI/grep/PDF，恢复图谱分析与向量检索能力
- evidence_count: 2
- repeated_times: 2
- suggested_artifact: 项目规则（Agent 行为准则）
- promote_decision: 已提升——本次会话即写入用户级 `~/.agents/AGENTS.md` gitnexus 段"MCP 在当前会话不可用时：先诊断修复，再考虑降级"小节，故标记 superseded（规则已生效，无需再 promotion）
- linked_entries: failure-2026-09-13-mcp-missing-misdiagnosis (KNOWN_FAILURES.md)

### Candidate: Item 子类继承符合性全量审计
- candidate_id: learn-2026-10-09-item-subclass-audit
- type: learning_candidate
- status: proposed
- confidence: verified（RegistrySpecification 一例已证实为真偏差并造成生产事故）
- last_updated: 2026-10-09
- source: 2026-10-09 registry-specification-model-correction 会话（错误继承 Item → 孤儿 → FK 失败 → 启动兜底 → 启动挂起 13 分钟事故链）
- owner: matrixlane
- review_after: 2026-12-09
- trigger: 新增/变更任何标准实体的继承关系；或定期审计任务
- repeated_pattern: 尚无第二例（白名单守护测试已锁定 15 个 extends Item 类的现状）；RegistrySpecification 是首个证实偏差
- impact: 高——继承偏差会级联产生孤儿数据、FK 约束失败、启动兜底逻辑与性能事故
- evidence_count: 1
- repeated_times: 1
- suggested_artifact: 独立 OpenSpec 变更（全量审计）+ `StandardInheritanceConformanceTest` 白名单条目引用补全（待审计占位替换为标准条款）
- promote_decision: 守护测试 + AGENTS.md 实体继承规则已于 2026-10-09 提升；全量审计本身留作待办变更
- linked_entries: 2026-10-09-registry-specification-model-correction（session-journal，待写）；AGENTS.md 实体继承规则
