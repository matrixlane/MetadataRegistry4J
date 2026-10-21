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

### BL-2026-10-20-01 关联表（registered_item_*_record/document）收紧 UNIQUE 约束前须先验证存量
- status: PROPOSED
- last_updated: 2026-10-20
- source: administered-item-reference-documents-ui 任务 2.x/7.5
- owner: matrixlane
- review_after: 2026-12-20
- trigger: 对 registered_item_submission_record / registered_item_reference_document 施加唯一约束
- repeated_pattern: 两表语义以 (两侧列) 组合为唯一键；V20261009011 只补了索引未加 UNIQUE
  （存量 11,182/23,449 行未验证无重复，贸然加约束会在存量库失败）
- impact: 中——不加约束，脚本/并发写入可能产生重复关联，查询去重靠 DISTINCT
- evidence_count: 1
- suggested_artifact: 新变更：先 GROUP BY 双侧列查重 → 去重规则（保留确定性一行 + 重定向引用）
  → 三方言 UNIQUE 迁移（幂等可重入），对齐 database-portability「收紧前先验证」要求
- linked_entries: openspec/changes/administered-item-reference-documents-ui（spec delta 已写入）

### BL-2026-10-20-02 workspace owner 判定慢路径（WorkspaceService 基线 9 失败同源）
- status: PROPOSED
- last_updated: 2026-10-20
- source: administered-item-reference-documents-ui 7.2 全量比对（5a5453ee 与 master 同口径 6F+3E）
- owner: matrixlane
- review_after: 2026-12-20
- trigger: 工作空间 drafts/rejected 视图与批量提交在 SQLite 上慢/失败；flags_* 用例 ArrayIndexOutOfBounds
- repeated_pattern: WorkspaceService 的 owner 归属判定与视图计数走深实体关联；SQLite 64 表 join
  上限缓解后仍有个别路径触顶/超时（既有基线失败，非本次变更引入）
- impact: 中——SQLite 部署下工作空间页签加载与批量操作不可用
- evidence_count: 2（10-09 基线 + 10-20 复测同口径）
- suggested_artifact: 对齐 64 表上限的治理方式：owner 判定改标量投影 + 视图计数改聚合子查询
- linked_entries: KNOWN_FAILURES failure-2026-09-21-sqlite-64-table-join-limit（resolved 后的残余路径）

### BL-2026-10-20-03 JPQL 标量投影的隐式路径 join 会被 Hibernate 生成为 INNER JOIN
- status: PROPOSED
- last_updated: 2026-10-20
- source: administered-item-reference-documents-ui 回归修复（1bd2699d 引入、本会话修复）
- owner: matrixlane
- review_after: 2026-12-20
- trigger: JPQL select 列表写多段点路径（如 ai.stewardshipRecord.contact.id）做标量取值
- repeated_pattern: Hibernate 对路径链生成 INNER JOIN——任一环为空的行整体丢行（不是该列 null）。
  AdministeredItemRepository.findMetaByItemId(s) 因此对无主管记录条目 meta 全 null，
  且掩盖了批量标识符路径的 NPE（路径不可达），10-17 注释自述"在途重构"即此
- impact: 高——静默丢行类 bug，集成测试少断言 meta 时不可见
- evidence_count: 1（事故级：列表/详情 meta 全空 + 两个潜伏 NPE）
- suggested_artifact: 项目约定：标量投影禁用多段点路径，一律显式 LEFT JOIN + 别名取列；
  可入 AGENTS.md「正交链完整性规则」配套查询形状约定
- linked_entries: 本会话纪要 2026-10-20-administered-item-reference-documents-ui

### BL-2026-10-20-04 Java 9+ 不可变 Map（Map.of/Map.copyOf）.get(null) 抛 NPE
- status: PROPOSED
- last_updated: 2026-10-20
- source: 同上回归修复（preferPrimaryScalarIdentifier / buildItemMeta 批量路径）
- owner: matrixlane
- review_after: 2026-12-20
- trigger: 用 Map.of() 兜底空 map，下游以可能为 null 的键查询
- repeated_pattern: ImmutableCollections$MapN.get 先 requireNonNull(key)——HashMap 返回 null 的
  宽容语义在不可变 map 上变成 NPE；防御习惯须改为先判空键
- impact: 中——仅批量/新路径受影响，但定位需看完整堆栈（findFirst 行号有误导性）
- evidence_count: 1
- suggested_artifact: 代码评审 checklist 一条；或统一用 HashMap 兜底代替 Map.of()
- linked_entries: 本会话纪要 2026-10-20-administered-item-reference-documents-ui

### BL-2026-10-20-05 多 Agent 并发同一仓库：验证必须在隔离 worktree，严禁共享 target/ 与 .m2 时段
- status: PROPOSED
- last_updated: 2026-10-20
- source: 本会话与 sqlite-empty-db-bootstrap 并行会话互踩的连环事故
- owner: matrixlane
- review_after: 2026-12-20
- trigger: 同一仓库两个以上 Agent/终端同时 mvn 构建
- repeated_pattern: 对方 clean 删我方 classes（NoClassDefFoundError 风暴）、.m2 SNAPSHOT 被
  基线实验覆盖（IdentifiedItem/RegisteredItem 编译错）、JVM 孤儿占文件锁、PowerShell 后台
  任务/管道提前返回制造"假完成"
- impact: 高——本会话为此消耗数小时排查环境假象
- evidence_count: 2（10-20 本会话多轮；历史 session-journal 亦有并行会话记录）
- suggested_artifact: AGENTS.md 或用户级规则：需要全量验证时先 git worktree add 隔离目录，
  Start-Process 显式 -WorkingDirectory，轮询 java PID 而非管道返回码，开工前 Get-Process java 清点
- linked_entries: 本会话纪要 2026-10-20-administered-item-reference-documents-ui
