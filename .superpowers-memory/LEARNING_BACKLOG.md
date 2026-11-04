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
- **2026-10-23 已解决**：baseline-data-init-rebuild 将 db-baseline/ 更名重构为 data/scripts/，.gitignore 收窄（data/*.db* 忽略、脚本/README/批处理入库跟踪），git check-ignore 双向断言通过
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

### BL-2026-10-22-01 sqlcmd 执行 UTF-8 SQL 脚本必须 `-f 65001`；Git Bash 传路径必须 `cygpath -w`
- status: PROPOSED
- last_updated: 2026-10-22
- source: administered-item-reference-documents-ui 收官会话（本机两次踩坑）
- owner: matrixlane
- review_after: 2026-12-22
- trigger: 用 sqlcmd `-i` 执行含中文注释/全角标点的 UTF-8 脚本，或从 Git Bash 传文件路径
- repeated_pattern: ①不带 `-f 65001` 时 sqlcmd 按 ANSI/GBK 代码页解读 UTF-8 字节，
  中文注释内全角括号/引号被误读 → 报 102「附近有语法错误」（错误行号指向注释，极具迷惑性）；
  ②Git Bash 的 `/e/...` POSIX 路径传给 Windows 版 sqlcmd 报「拒绝访问」
- impact: 中——报错信息与真实原因（编码/路径）完全无关，排错成本高
- evidence_count: 2（本日 reference-link NOEXEC 102、final_session 拒绝访问）
- suggested_artifact: db-baseline/README.md 增补「执行通则」一节；scratch 脚本模板头注
- linked_entries: 2026-10-22-administered-item-refdocs-realdb.md 环境备忘

### BL-2026-10-22-02 Hibernate 6 join elimination 使「JOIN 实体仅取其 id」的 JPQL 不再触碰该表
- status: PROPOSED
- last_updated: 2026-10-22
- source: 1.3 红态复现失败排查（show-sql 取证）
- owner: matrixlane
- review_after: 2026-12-22
- trigger: JPQL `JOIN e.association a WHERE a.id = :x` 且 select 列表只用 e 的字段
- repeated_pattern: Hibernate 把对端实体 join 折叠为 join 表外键列比较——
  `SubmissionRecord` 案例实际 SQL 只 join `registered_item_submission_record`，
  完全不触 `registered_item`（30+ 子类继承展开不存在）。
  2026-10-10 的 40s+ 事故态（空库无统计信息+无索引+旧计划）在当前库上不可复现
- impact: 低（对运行时是好事）——但「回退某查询复现历史性能事故」类验证会因此拿不到红态，
  证据口径必须改凭事故时记录
- evidence_count: 1（红绿对照 + show-sql）
- suggested_artifact: 性能回退验证方法论入 superpowers 会话模板：先抓 show-sql 确认查询形状，再谈复现
- linked_entries: tasks.md 1.3 收官记录

### BL-2026-10-22-03 列表接口大页触发 JDBC 2100 参数上限（既有约束）
- status: PROPOSED
- last_updated: 2026-10-22
- source: 6.4 边界实测 data-elements?size=10000 → HTTP 500
- owner: matrixlane
- review_after: 2026-12-22
- trigger: 分页 size 超过约 2000（IN 列表参数超 SQL Server 2100 上限）
- repeated_pattern: designation 批量查询 `WHERE item_id IN (10000 个参数)` 直接由驱动拒绝；
  与本次变更无关，属既有上限
- impact: 中——「查看全部」类前端诉求在 SQL Server 下不可用
- evidence_count: 1
- suggested_artifact: 新变更：批量 IN 改 chunked（≤1000/批）或临时表 join；前端分页上限联动
- linked_entries: 2026-10-22-administered-item-refdocs-realdb.md

### BL-2026-10-22-04 ItemReferenceDocumentsSection 增删为即时 API 调用，与 4.4「保存时 diff」描述不同步
- status: PROPOSED
- last_updated: 2026-10-22
- source: 6.5 验收实测（新增/移除均即时落库，乐观更新+失败回滚）
- owner: matrixlane
- review_after: 2026-12-22
- trigger: 阅读 tasks/spec 预期「保存时对比差异集提交」，实际实现走即时 POST/DELETE
- repeated_pattern: ItemFormDialog addReferenceDocuments/removeReferenceDocuments 立即调用
  administeredItemReferenceApi 并在失败时回滚乐观变更；表格行数据在即时新增后不刷新，
  重开对话框前需重新拉取（脚本里表现为列表不带新行）
- impact: 低——行为更即时可用，但文档与实现两处事实源不一致，后续改动易误判
- evidence_count: 1（UI 全流程 + API 计数验证）
- suggested_artifact: 轻量对齐：改 4.4 描述或改实现为保存时 diff（建议改描述，保留即时交互）
- linked_entries: 2026-10-22-administered-item-refdocs-realdb.md

### BL-2026-10-23-01 旁路实例数据源隔离的正确姿势
- status: READY
- last_updated: 2026-10-23
- source: baseline-data-init-rebuild 会话（三次误连活库事故）
- 要点：LocalConfigBootstrapInitializer 无条件加载 ~/.metadata-registry/application-local.yml（字面量 url 压过 profile 占位符里的 DB_URL 环境变量）；隔离须用 JAVA_TOOL_OPTIONS="-Dmetadata.registry.config.file=<side.yml>"（spring-boot:run 的 -Dspring-boot.run.jvmArguments 引号经 mvnw.cmd+bash 传递不可靠）；side yml 对 sqlite 必须自带 flyway.schemas/default-schema=main（EXISTING 态无 FRESH 注入，空 schemas 会让 flyway 查 "".flyway_schema_history 炸掉）；TaskStop 杀不掉 fork 的 java 进程，须按端口杀 PID。
- linked_entries: 2026-10-23-baseline-data-init-rebuild.md

### BL-2026-10-23-02 sqlite 时间戳存储形态必须与 sqlite-jdbc 运行时一致
- status: READY
- last_updated: 2026-10-23
- source: baseline-data-init-rebuild 会话（预置库列表页 500 实测）
- 要点：sqlite-jdbc 3.49 setTimestamp 落库 epoch 毫秒 INTEGER；getTimestamp 可读 INTEGER 与空格分隔 TEXT，T 分隔 ISO TEXT 必炸（"Error parsing time stamp"）。裸 SQL 脚本写时间戳必须写 epoch 毫秒 INTEGER；历史 T 分隔残留（12,581 值）已在基准重建时归一。
- linked_entries: 2026-10-23-baseline-data-init-rebuild.md；scratch/baseline-rebuild/verification-report.md §5.2

### BL-2026-10-23-03 Windows 单文件 bat+PowerShell 混合脚本工程要点
- status: READY
- last_updated: 2026-10-23
- source: baseline-data-init-rebuild 会话（init-baseline.bat 五轮调试）
- 要点：bat 必须 CRLF（LF 下 cmd 解析错乱执行注释碎片）；bat 头注释保持纯 ASCII（UTF-8 中文 rem 被 cmd 按 GBK 误读可产生 & 碎片）；PS 5.1 不支持 ?? 运算符；PS 5.1 调原生程序吞参数内双引号（python -c 内嵌 SQL 惨案）→ 临时 .py 助手文件/sqlite3 stdin 喂入；split 标记不得在提取命令行自身出现；sqlcmd 用 host,port 逗号（JDBC 是冒号）。
- linked_entries: 2026-10-23-baseline-data-init-rebuild.md

### BL-2026-10-23-04 fresh schema（纯 ddl-auto）与迁移库 schema 存在多处发散
- status: READY
- last_updated: 2026-10-23
- source: baseline-data-init-rebuild 会话的活库 vs fresh 库 INFORMATION_SCHEMA 全量比对（scratch/baseline-rebuild/diff-live-vs-scripts.md 配套审计）
- 已修复：`DataType.annotation`/`PermissibleValue.permittedValue` 加 @Nationalized（迁移产物 NVARCHAR vs fresh VARCHAR 致数据丢失）。
- 待跟进（本次未扩 scope）：① fresh 库缺 `ext.identifier_sequence_registry`（RegistrationAuthorityDeletionService 的 qualifyAuto 裸 SQL 路径在 fresh SQL Server 库会踩空表）；② `registration_transition_rule_approver`（@ElementCollection 集合表无法经 orm.xml 登记）fresh 落 mdr 而迁移库在 ext，裸 SQL 路径同险；③ 16 列可空性 fresh 更严（NOT NULL），与迁移库 nullable 不一致（基准数据已实证满足 NOT NULL，影响仅在未来迁移写入路径）。建议：新开平台级变更做「fresh schema 与迁移链产物全量对齐」或确立「fresh 即以 ddl-auto 为准、迁移仅服务存量」的原则并补齐守护测试。
- linked_entries: 2026-10-23-baseline-data-init-rebuild.md
