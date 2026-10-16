# repair-administered-item-closure 任务清单

> 依据 `specs/`（做什么）与 `design.md`（怎么做）拆分。每项均标注验证方式。
> **实施顺序即依赖顺序**：TDD 红 → 阻断项解除 → 补壳 → 守护转绿 → 文档。

## 1. 建立缺陷复现证据（TDD 红）

- [x] 1.1 在 `metadata-registry-web` 测试目录 `standard/` 包下新建 `AdministeredItemClosureConformanceTest`，实现闭环不变式断言（反连接查「有 `identified_item` 无 `registered_item`」的违反集，断言为空）——验证：编译通过
- [x] 1.2 在不变式查询中加入「逐条报出违反条目标识符」的断言路径——验证：单元用例以人为构造的违规样本驱动，断言输出含该条目标识符而非仅计数
- [x] 1.3 在 SQLite 副本库上准备"裸 SQL 基准还原但未补壳"的场景，运行守护测试——验证：**测试必须失败**并列出违反条目，构成缺陷可复现的红态证据（记入 session-journal）

## 2. 解除阻断项 A：CHECK 约束对齐标准值集

- [x] 2.1 编写 `V20261009009__Align_registration_status_check.sql`（SQL Server 版）：动态查询 `sys.check_constraints` 取实际约束名，DROP 后按标准十值集重建——验证：脚本内不出现硬编码约束名
- [x] 2.2 编写同版本号 SQLite 版：重建表（建新表 → `INSERT…SELECT` → `DROP` → `RENAME`），显式保留索引与 FK，带"已是标准值集则 no-op"幂等守卫——验证：脏库与干净库各执行一次均成功
- [x] 2.3 编写同版本号达梦版：全限定 `MDR.REGISTRATION_STATE`，语法对齐 SQL Server——验证：脚本齐备（未实测，记遗留）
- [x] 2.4 在 SQLite 副本库与真实 SQL Server 库副本上应用迁移——验证：事务内 `PREFERRED_STANDARD` 写入被**接受**、`CANDIDATE` 被接受、`SUBMITTED` 与 `PREFERRED` 被**拒绝**
- [x] 2.5 验证迁移幂等——验证：已是标准值集的库上重复执行无报错、无数据变更

## 3. 解除阻断项 B：SQLite 64 表 join 上限

- [x] 3.1 在 `AdministeredItemRepository` 新增只取归属注册机构标识的投影查询（返回 `Optional<UUID>` 或等价形态，不返回实体）——验证：新增单元测试断言返回形态正确
- [x] 3.2 `ActiveRegistrarService.assertCanModifyItem`（现 `:166-176`）改用该投影：无行或 RA 为 null 放行，有行走既有 `assertActiveIdentityOf`——验证：`ActiveRegistrarService` 既有测试全绿
- [x] 3.3 SQLite 上投影查询的功能性验证（`resolvesRegistrationAuthorityViaProjection` /
      `projectionReturnsEmptyForUnadministeredItem`，含完整受管链）——验证：SQLite 内存库上返回正确归属、
      无受管实例时返回空；日志无 `at most 64 tables in a join`
- [x] 3.4 （并入第 10 组）应用级 update/delete 冒烟：真实库应用后跑一次数据元编辑，
      确认个人版（SQLite）不再 500——当前运行实例为 SQL Server 团队版，未覆盖 SQLite 应用级路径

## 4. 基准生成器修正（须先于补壳脚本落地）

- [x] 4.1 `scratch/ws-verify/gen_baseline_sql.py` 的 `TABLES` 常量纳入 `registered_item`、`administered_item`、`registration_state`——验证：常量大写后含三张表
- [x] 4.2 生成器前置闭环断言：源库存在未闭合受管链即拒绝生成（`assert_closure`）——验证：在缺环库上运行生成器应以非零退出并报出违反条数
      （定案修订：补壳语句生成逻辑改由独立的 `db-baseline/ws363-364-administered-item-*.sql` 承载，
        生成器只负责按表全量导出 + 拒绝在缺环源库上导出——后者保证缺陷不会被重新固化，
        且避免在要求 UUID 逐字节一致的生成路径中引入随机 UUID）

## 5. 基准补壳脚本

- [x] 5.1 编写 `db-baseline/ws363-364-administered-item-sqlite.sql`：集合式补 `registered_item` + `administered_item` + `registration_state`，状态 `PREFERRED_STANDARD`、`effective_date='2024-04-01'`、`previous_state_id` 与 `registration_authority_id` 留空、`origin` 填可追溯标记，UUID 用 `randomblob` 拼 v4，整体包 `BEGIN TRANSACTION`/`COMMIT`——验证：脚本中无硬编码行数
- [x] 5.2 编写同内容的 `ws363-364-administered-item-sqlserver.sql`（UUID 用 `NEWID()`）——验证：脚本齐备
- [x] 5.3 两脚本幂等性验证——验证：对已补壳库重复执行，无重复主键、不改动既有状态与生效日期
- [x] 5.4 集合式覆盖验证——验证：在行数与生成源不同的库上执行，覆盖全部「有 identified 无 registered」条目，脚本无需修改

## 6. 应用补壳并转绿

- [x] 6.1 在 SQLite 副本库上执行补壳脚本——验证：脚本成功，"有 identified 无 registered"计数归零
- [x] 6.2 重跑 `AdministeredItemClosureConformanceTest`——验证：**由红转绿**
- [x] 6.3 数据一致性冒烟：抽查数据元对话框数据源链路，确认注册状态读出 `PREFERRED_STANDARD`、生效日期为 `2024-04-01`——验证：API 返回值符合预期
- [x] 6.4 回归：后端全量测试与既有基线失败集合对比——验证：无新增失败
- [x] 6.5 回归：前端 `vitest` 全量——验证：见第 7 节改动后全绿

## 7. 前端：移除注册状态推进流程提示

- [x] 7.1 删除 `ItemRegistrationStateSection.vue`（`:14-21`）的 `el-alert` 提示块及 `readonly-status-hint` class——验证：编辑态渲染无该提示块
- [x] 7.2 删除 `zh-CN.json:338` 与 `en-US.json:320` 的 `itemRegistrationState.editHint`——验证：两份语言文件均无该 key
- [x] 7.3 改 `ItemRegistrationStateSection.spec.ts`：编辑态断言改为**不存在**提示，保留"编辑态无 `ElSelect`""新建态下拉恰为 3 项（不完整/历史/应用）"断言——验证：该 spec 全绿
- [x] 7.4 前端 `vitest` 全量 + `build`——验证：全绿、构建通过

## 8. 规则与文档同步

- [x] 8.1 `AGENTS.md` 在「实体继承规则（强制）」旁新增「正交链完整性规则（强制）」：裸 SQL 导入 MUST 自建完整关联链；已标识条目 SHALL 沿 §9.4.1.2 落到 Administered_Item 或 Attached_Item；验收 MUST 含语义不变式断言——验证：规则文本落地且与 specs 措辞一致
- [x] 8.2 `db-baseline/README.md` 增补补壳脚本作为还原第 2 步，修正已过期的「仅 SQLite 方言」等限制描述——验证：还原步骤可直接照做
- [x] 8.3 将 `failure-2026-09-21-sqlite-64-table-join-limit` 在 `KNOWN_FAILURES.md` 标记为已修复并说明修法——验证：条目状态更新
- [x] 8.4 将「恢复 `data/metadata-registry.db` 生成源」「WS 218/365–375 导入的同类闭环风险未评估」「达梦方言实测」记入 `LEARNING_BACKLOG.md`——验证：三项均有条目

## 9. 提交前检查

- [x] 9.1 运行 `detect-changes`（`node .gitnexus/run.cjs detect-changes --scope all --repo .`）——验证：无 `partial: true`、无 `truncated: true`
- [x] 9.2 `openspec validate repair-administered-item-closure`——验证：valid
- [x] 9.3 `openspec validate --all`——验证：全部能力 valid，无回归

## 10. 真实库应用（**需用户单独确认后执行**）

- [x] 10.1 备份真实库 `metadata_registry` 至 `scratch/`——验证：备份文件存在且可读
- [x] 10.2 应用 `V20261009009`——验证：`PREFERRED_STANDARD` 写入被接受
- [x] 10.3 执行 SQL Server 补壳脚本——验证：闭环守护测试在真实库转绿
- [x] 10.4 真实库冒烟（临时实例，端口 8085，team profile 连真实库）：`GET /api/v1/data-elements`
      中用户最初截图的那条 `DE02.01.039.89` 返回 `registrationStatus=PREFERRED_STANDARD`、
      `stateEffectiveDate=2024-04-01T00:00:00`、`administeredItemId` 非空、
      `namespaceLabel=卫生信息标准命名空间`——验证：API 与界面渲染同源（界面直接渲染
      `meta.registrationStatus`），用户原始场景闭环；验证后已停掉该临时实例
- [x] 10.5 在 `.superpowers-memory/session-journal/` 记录本次会话，更新 `CURRENT_STATE.md`——验证：记录含根因、标准条款引用与验证证据

---

## 实施记录（2026-10-09）

### 验证证据

| 项 | 结果 |
| --- | --- |
| 守护测试红态证据 | 真实历史库 `data/metadata-registry.db.bak-preassoc-20260914.db`：registered_item / administered_item / attached_item **均 0 行**，2328 个已标识条目 **100% 未闭环** |
| CHECK 约束（SQL Server 真实库副本） | 迁移前 `PREFERRED_STANDARD` / `CANDIDATE` 均 REJECTED；迁移后二者 ACCEPTED，`SUBMITTED` / `PREFERRED` REJECTED，`Standard`（标准书写形式）ACCEPTED |
| CHECK 约束（SQLite） | 同上；另验证归位 `SUBMITTED→CANDIDATE`、`PREFERRED→PREFERRED_STANDARD`，行数守恒，索引保留 |
| 迁移幂等 | SQL Server 连跑 3 次 EXIT=0，约束数恒 1、索引数恒 3；SQLite 连跑 2 次行数不变 |
| 补壳（SQL Server 副本库） | 11,184 未闭环 → 0；11,185 行 `PREFERRED_STANDARD` @ `2024-04-01`；连跑 2 次行数不变、无状态引用重复 |
| 补壳（SQLite） | 2328 未闭环 → 0；全新 / 全量重跑 / 中断恢复三场景均通过，无重复 PK |
| 端到端取值 | SQLite 全链路四表 join：2328/2328 条目读出 `PREFERRED_STANDARD` + `2024-04-01` |
| 后端回归 | 1499 用例，12 失败。**干净 HEAD 对照组同样 48 用例 / 8 失败 / 4 错误**（同一 4 个类），零新增回归 |
| 本次相关测试 | `AdministeredItemClosureConformanceTest` 7/7、`ActiveRegistrarServiceTest` 19/19、`CaseInsensitiveEnumPersistenceTest` 2/2 |
| 前端 | vitest 35 文件 / 332 用例全绿；`npm run build` 通过 |
| OpenSpec | `validate repair-administered-item-closure` valid；`validate --all` 72 passed / 2 failed（均为并行会话在途变更） |

### 实施期发现并修正的自身缺陷

1. **迁移归位顺序错误**：初版把 `UPDATE ... SET registration_status='CANDIDATE' WHERE ...='SUBMITTED'` 排在删 CHECK 之前，被旧约束自身拒绝（SQLite `CHECK constraint failed`）。真库恰无 SUBMITTED/PREFERRED 行，SQL Server 上未暴露；构造含旧值的数据后立即暴露。修正为「先删约束 → 再归位 → 再加约束」；SQLite 版改为把归位折进 `INSERT…SELECT` 投影（CASE 表达式），新表 CHECK 校验已归位值。
2. **补壳脚本配对顺序错误**：`_closure_free`（未使用状态行集）在插入新状态行**之前**建立，导致第 3 步 join 为空、`administered_item` 插入 0 行。修正为在建表前插入状态。
3. **补壳脚本部分补壳不可恢复**：初版仅在"全未补壳"状态下正确；中途失败后重跑会撞 `registration_state_id` UNIQUE。改为按"缺哪层补哪层"分配未使用状态行，`broken.db` 删 300 行后重跑可补齐。
4. **版本号冲突**：`V20261009009` 已被并行会话的 `Drop_stale_legacy_tables` 占用并已应用，新迁移改用 `V20261009010`。
5. **`sqlcmd` 未加 `-f 65001`**：UTF-8 中文注释被截断导致伪语法错误（项目 KNOWN_FAILURES 已记录此坑，本次仍踩中一次）。

### 真实库状态：**已应用**（用户 2026-10-09 确认）

| 步骤 | 结果 |
| --- | --- |
| 备份 | `scratch/closure-repair/pre-closure-apply-184743.bak`（25 MB） |
| 应用 V20261009010 | 成功；约束行为即时生效（`PREFERRED_STANDARD`/`CANDIDATE` ACCEPTED，`SUBMITTED`/`PREFERRED` REJECTED） |
| 执行补壳脚本 | 11,184 未闭环 → **0**；11,184 行 `PREFERRED_STANDARD` @ `2024-04-01` |
| 幂等重跑 | EXIT=0，三表行数不变（11,185 / 11,185 / 11,185） |
| Flyway 登记 | 临时实例启动时 Flyway 正式应用并登记 `20261009010`（对已手动执行的约束为幂等 no-op） |
| 端到端冒烟 | `DE02.01.039.89` → `PREFERRED_STANDARD` + `2024-04-01T00:00:00` |
| 完整性 | 无状态引用重复；无 `previous_state` 伪造；11,184 条 RA 留空（既有 1 条 CTX.00001 保持 RECORDED） |

验证全程用独立副本库 `metadata_registry_clo` 与 SQLite 副本先行完成，确认无误后才落到真实库。
补壳前后各留有一次全库备份。

### 待用户裁定

- **BL-2026-10-09-06**：`db-baseline/` 被 `.gitignore` 整体忽略（`git ls-files` 为空），
  与其 README「本目录被 git 跟踪」的声明不符。本次新增的两个补壳脚本同样不会被提交。
  `.gitignore` 注释含「不得签入（需重新核准后再放行）」，属显式策略，未擅自 force-add。
