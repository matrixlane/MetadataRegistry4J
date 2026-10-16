# 注册库内容缺失受管项实例闭环修复 —— 实施计划

日期：2026-10-09
OpenSpec change：`repair-administered-item-closure`
设计稿：`docs/superpowers/specs/2026-10-09-administered-item-closure-repair-design.md`

## 1. 实施位置

**主树 `master` 直接实施**，不另开 worktree。理由：

- 主树未提交改动仅 4 个文件（`Sign.java`、`Text.java`、`ContactExtensionRepository.java`、
  `V20260525001__Add_performance_indexes.sql`），与本变更触及的文件**无交集**；
- 本变更需在真实运行的实例上验证（用户正在 `javaw` 跑 jar + SQL Server 真实库），
  隔离 worktree 会让验证结果无法直接呈现。

新迁移版本 `V20261009009` 与被并行修改的 `V20260525001` 不同文件，无版本号冲突。

## 2. 关键前置约束（实施时不得违背）

1. **补实例，不改继承**：`DataElement extends Item`、`IdentifiedItem extends Entity`
   均不动（11179-31 §7.2.2.4.1、§7.3.1 无 Direct superclass）。
2. **迁移必须先于补壳脚本**：`PREFERRED_STANDARD` 写入依赖 CHECK 约束修正先落地。
3. **生成器修正必须先于补壳脚本落地**：否则恢复生成源后重导会复发。
4. **真实库写入需用户单独确认**（tasks 第 10 组）。
5. 每个文件编辑前按 AGENTS.md 跑 `impact`；提交前跑 `detect-changes`。

## 3. 执行批次

### 批次 A — 建立红态证据（第 1 组）

1. 新建 `AdministeredItemClosureConformanceTest`（`metadata-registry-web` 测试
   `standard/` 包）。断言：反连接查「有 `identified_item` 无 `registered_item`」的
   违反集为空。
2. 该测试走 `JdbcTemplate` 直查（与 `StandardInheritanceConformanceTest` 一致的
   纯反射风格不同——本条是数据不变式，需真实 DB）。
3. 在 SQLite 副本库上运行，**确认失败并列出违反条目** → 记入 session-journal 作为
   缺陷复现证据。

**退出判据**：测试存在且在缺陷库上红。

### 批次 B — 解除阻断项 A：CHECK 约束（第 2 组）

1. `V20260901001` 不可复用（版本号 ≤ `baseline-version=20260918002`，Flyway 必跳过）。
   新起 `V20261009009`。
2. SQL Server 版：先查 `sys.check_constraints` 取实际约束名（动态，不硬编码），
   DROP + 按标准十值集重建。
3. SQLite 版：重建表（建新表 → `INSERT…SELECT` → `DROP` → `RENAME`），
   显式保留索引与 FK，幂等守卫。
4. 达梦版：全限定 `MDR.REGISTRATION_STATE`，语法对齐 SQL Server（未实测，记遗留）。
5. 验证：事务内写入 `PREFERRED_STANDARD`/`CANDIDATE` 接受、`SUBMITTED`/`PREFERRED` 拒绝；
   重复执行 no-op。

**退出判据**：两方言库上四项写入行为均符合标准十值集。

### 批次 C — 解除阻断项 B：SQLite join（第 3 组）

1. `AdministeredItemRepository` 加投影查询：只取 `registrationAuthority.id`，
   返回 `Optional<UUID>`（或等价），不返回实体。
2. `ActiveRegistrarService.assertCanModifyItem`（现 `:166-176`）改用之。
   RA 为 null 或无行 → 放行；否则走既有 `assertActiveIdentityOf`。
3. SQLite 副本库上对**具备受管项**的条目做 update/delete 冒烟，确认无 64 表 join。

**退出判据**：SQLite 上带受管项条目可写可删。

### 批次 D — 生成器与补壳脚本（第 4、5 组）

1. `scratch/ws-verify/gen_baseline_sql.py` 的 `TABLES` 纳入
   `registered_item` / `administered_item` / `registration_state`，补补壳语句生成逻辑。
2. `db-baseline/ws363-364-administered-item-sqlite.sql`：集合式补三行，
   `PREFERRED_STANDARD` + `effective_date='2024-04-01'`，`previous_state_id` 与
   `registration_authority_id` 留空，`origin` 填可追溯标记；
   UUID 用 `randomblob` 拼 v4；包 `BEGIN TRANSACTION`/`COMMIT`。
3. `…-sqlserver.sql`：同内容，UUID 用 `NEWID()`。
4. 幂等 + 集合式（无硬编码行数）双验证。

**退出判据**：脚本在行数不同的库上均正确补壳且可重跑。

### 批次 E — 应用与转绿（第 6 组）

1. SQLite 副本库执行补壳脚本。
2. `AdministeredItemClosureConformanceTest` **由红转绿**。
3. 抽查数据元注册状态链路，读出 `PREFERRED_STANDARD` + `2024-04-01`。
4. 后端全量 + 前端全量回归，与既有基线失败集对比无新增。

**退出判据**：守护测试绿、无新增回归。

### 批次 F — 前端与文档（第 7、8 组）

1. 删 `ItemRegistrationStateSection.vue:14-21` 的 `el-alert` + `readonly-status-hint`。
2. 删 `zh-CN.json:338`、`en-US.json:320` 的 `itemRegistrationState.editHint`。
3. 改 `ItemRegistrationStateSection.spec.ts`：编辑态断言改为**不存在**提示，
   保留「编辑态无 `ElSelect`」「新建态 3 项下拉」。
4. `AGENTS.md` 新增「正交链完整性规则（强制）」。
5. `db-baseline/README.md` 增补还原第 2 步并修正过期限制描述。
6. `KNOWN_FAILURES.md` 标记 `failure-2026-09-21-sqlite-64-table-join-limit` 已修复。
7. `LEARNING_BACKLOG.md` 记入三项遗留（恢复生成源 / WS 218·365–375 风险评估 / 达梦实测）。

**退出判据**：vitest 全绿 + build 通过；文档三处落地。

### 批次 G — 提交前检查（第 9 组）

`detect-changes`（无 partial/truncated）→ `openspec validate` 本变更 → `openspec validate --all`。

### 批次 H — 真实库应用（第 10 组，**需用户单独确认**）

备份 → `V20261009009` → 补壳脚本 → 守护测试转绿 → 界面冒烟 → session-journal。

## 4. 验证命令

```bash
# 后端目标测试
mvn -pl metadata-registry-web -am test -Dtest='AdministeredItemClosureConformanceTest'

# 后端全量
mvn -pl metadata-registry-web -am test

# 前端
cd metadata-registry-ui && npx vitest run && npm run build

# OpenSpec
openspec validate repair-administered-item-closure
openspec validate --all

# 图谱分析
node .gitnexus/run.cjs detect-changes --scope all --repo .
```

## 5. 风险与应对

| 风险 | 应对 |
|---|---|
| SQLite 重建表丢索引/FK | 迁移内显式重建 + 冒烟断言 |
| SQL Server 约束名未知 | 迁移动态查 `sys.check_constraints`，不硬编码 |
| 补壳脚本 UUID 三行不一致 | SQLite 脚本内先物化映射表或用 `WITH` 子句保证同值 |
| 补壳后回归爆炸 | 批次 C 必须先于批次 E 完成，否则 SQLite 冒烟会假失败 |
| 真实库不可逆 | 备份 + 幂等 + 单独确认 |

## 6. 与 tasks.md 的对应

本计划覆盖 `tasks.md` 第 1–9 组全部；第 10 组（真实库）待用户确认后执行。