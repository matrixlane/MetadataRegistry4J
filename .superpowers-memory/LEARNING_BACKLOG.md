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

### BL-2026-10-09-03 恢复 WS 363/364 基准生成源 data/metadata-registry.db
- status: READY
- last_updated: 2026-10-09
- source: 2026-10-09 repair-administered-item-closure 会话
- owner: matrixlane
- review_after: 2026-12-09
- trigger: 需要重新生成 `db-baseline/ws363-ws364-baseline-*.sql` 时
- impact: 中——生成源已丢失，`scratch/ws-verify/gen_baseline_sql.py` 与 `adapt_baseline_sqlserver.py`
  当前均无法运行；两个 11MB 基准脚本冻结在 2026-10-08 状态，后续基准数据变更无法回灌脚本
- evidence_count: 1
- suggested_artifact: 恢复一份干净基准库（或改为从脚本自身反向生成），并把生成流程纳入
  `db-baseline/README.md` 的可复现步骤
- promote_decision: 待办；本次以独立补壳脚本 `db-baseline/ws363-364-administered-item-*.sql`
  绕过对生成源的依赖，且已给生成器加前置闭环断言（源库不闭环即拒绝生成）
- linked_entries: openspec/changes/repair-administered-item-closure

### BL-2026-10-09-04 WS 218 / 365–375 等其余卫生行业标准的数据导入闭环风险未评估
- status: READY
- last_updated: 2026-10-09
- source: 2026-10-09 repair-administered-item-closure 会话
- owner: matrixlane
- review_after: 2026-12-09
- trigger: 决定导入 WS 218 / 365–375 等其他卫生行业标准时
- impact: 中——若沿用裸 SQL 还原路径，会重现「已标识条目停在 Identified_Item」的闭环缺陷；
  闭守护测试 `AdministeredItemClosureConformanceTest` 可提前发现，但需先纳入该库的冒烟
- evidence_count: 1
- suggested_artifact: 导入前先核对 §9.4.1.2 闭环不变式；导入脚本 MUST 自建受管链
- promote_decision: 待办；AGENTS.md「正交链完整性规则」已写入强制条款作为第一道防线
- linked_entries: openspec/changes/repair-administered-item-closure

### BL-2026-10-09-05 达梦方言 V20261009010 未实测
- status: READY
- last_updated: 2026-10-09
- source: 2026-10-09 repair-administered-item-closure 会话
- owner: matrixlane
- review_after: 2026-12-09
- trigger: 部署达梦版本时
- impact: 低——SQL Server / SQLite 两方言已实测通过（写入行为、归位、幂等、行数守恒均验证）；
  达梦版语法对齐 SQL Server 版，按项目既有惯例未实测
- evidence_count: 1
- suggested_artifact: 在达梦实例上执行 `V20261009010__Align_registration_status_check.sql`
  与 `ws363-364-administered-item-*`，重点验 CHECK 重建语法与 `EXECUTE IMMEDIATE` 动态 DDL
- promote_decision: 待办；沿用既有 backlog 项（达梦方言长期未实测）
- linked_entries: openspec/changes/repair-administered-item-closure

### BL-2026-10-09-06 db-baseline/ 实际未被 git 跟踪（用户确认为有意暂缓）
- status: ON_HOLD（用户 2026-10-09 明确裁定：忽略是有意为之，待本地数据验证完成后另行要求签入）
- last_updated: 2026-10-09
- source: 2026-10-09 repair-administered-item-closure 实施期发现
- owner: matrixlane
- review_after: 2026-12-09
- trigger: 提交本次新增的 `db-baseline/ws363-364-administered-item-*.sql` 时
- impact: 高——`db-baseline/README.md` 明确写着"本目录被 git 跟踪"，理由是"上次教训：
  数据只放在被 git 忽略的 data/ 目录里，机器间无法复现"。但 `.gitignore:20` 仍写着
  `db-baseline/`（注释为"未核准的基准数据快照，不得签入"），且 `git ls-files db-baseline/`
  返回**空**。即 23MB 基准脚本（含本次两个补壳脚本）都只存在于本机，
  "新环境/新开发机一键落地 WS 基准数据"的既定用途实际不成立
- evidence_count: 1
- repeated_times: 1
- suggested_artifact: 二选一并落到文档——(a) 移除 `.gitignore` 中的 `db-baseline/` 并
  `git add -f` 全量签入（兑现 README 声明）；(b) 保留忽略规则但改写 README，
  把"被跟踪"改为"仅本机留存"，并把基准数据的跨机分发方式另行定义
- promote_decision: **用户 2026-10-09 裁定：`.gitignore` 中的 `db-baseline/` 忽略是有意为之，
  当前不需要签入**；待本地数据验证工作完成后用户会再行要求签入。故本次及后续在验证期内
  均不 force-add，唯一例外是用户显式下达签入指令时。
  → `db-baseline/README.md` 中"本目录被 git 跟踪"的表述在验证期内与实际不符，
  已在该文件开头加注（见 README「⚠️ 还原需两步」小节）。
- linked_entries: openspec/changes/repair-administered-item-closure；db-baseline/README.md
