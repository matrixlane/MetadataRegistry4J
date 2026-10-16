# 受管项参考文档展示与提交关联查询修复 —— 实施计划

日期：2026-10-09
OpenSpec change：`administered-item-reference-documents-ui`
设计稿：`docs/superpowers/specs/2026-10-09-administered-item-reference-documents-ui-design.md`

## 1. 实施位置

**主树 `master` 直接实施**，不开 worktree。理由：
- 主树未提交改动仅 4 个并行会话文件（`Sign.java`、`Text.java`、
  `ContactExtensionRepository.java`、`V20260525001`），与本变更触及的文件无交集；
- 用户正以 jar 方式运行实例，从源码构建不影响其运行中的 jar；
- 上一变更（`repair-administered-item-closure`）已确立主树实施且用户验证通过。

新迁移版本占用：`V20261009011`（索引）、`V20261009012`（表合并）。
已确认 `V20261009011`/`12` 未被占用（当前最高为 `20261009009`）。

## 2. 关键前置约束（实施时不得违背）

1. **元模型不改**：`RegisteredItem extends IdentifiedItem` 的继承声明保持，
   `StandardInheritanceConformanceTest` 白名单与条款引用**一字不改**。
2. **迁移顺序不可颠倒**：`V20261009010`（CHECK，已应用）→ `V20261009011`（索引）
   → `V20261009012`（表合并）。
3. **提交关联最后恢复**：D1 修复验证通过前不挂 `registered_item_submission_record`
   （挂上必超时，无法区分修复失败与关联失败）。
4. **真实库写入需用户确认**：第 6 组全部操作。
5. 每个文件编辑前按 AGENTS.md 跑 `impact`；提交前跑 `detect-changes`。

## 3. 执行批次

### 批次 A — 解除阻塞：提交记录查询（第 1 组）

1. `SubmissionRecordRepository` 新增按 `registered_item_id` 取
   `submission_record_id` 的查询，经 `DatabaseEnvironment.qualify` 方言中立限定。
2. 改写 `findAllByRegisteredItemIdOrderByIdDesc`：取 id → `findAllById` →
   内存按 id 降序。**不再经 `JOIN sr.registeredItems`**。
3. 红态证据：真实库挂满 11,182 条关联时，修复前 >40s 超时、修复后 <1s，两组均记录。

**退出判据**：真实库挂满关联下列表接口亚秒级。

### 批次 B — 索引迁移（第 2 组）

`V20261009011` 三方言：两张关联表各两个 join 列索引，SQL Server 动态取名，
SQLite `CREATE INDEX IF NOT EXISTS`，达梦全限定。

**退出判据**：SQL Server 副本上 4 个索引存在；`SET STATISTICS IO` 断言检索
不随表规模线性增长。

### 批次 C — 参考文档取值（第 3 组）

1. 批量取参考文档 id 的仓储方法（一次传入本页全部 `registered_item_id`）。
2. `ItemMetaResponse` 增 `List<UUID> referenceDocumentIds`。
3. `buildItemMeta` 增可选批量入参，Service 列表路径统一预取后传入。
4. 接口实测 `GET /api/v1/data-elements?size=20`，关联一致且耗时不线性增长。

**退出判据**：编译通过；列表接口返回正确关联且亚秒级。

### 批次 D — 前端参考文档页签（第 4 组）

1. `types/common.ts` 补 `meta.referenceDocumentIds`。
2. 新建参考文档选择组件（远程搜索多选，展示 `identifier` + `title`）。
3. `ItemFormDialog` 插入 `referenceDocuments` 页签，渲染条件
   `showRegistration && form.meta.administeredItemId`。
4. 保存时对差异集调用既有 POST / DELETE 端点。
5. `vitest` 全量 + `build`。

**退出判据**：全绿、构建通过。

### 批次 E — 受管链物理表合并（第 5 组）

1. `IdentifiedItem` → `@MappedSuperclass`（去 `@Entity`/`@Inheritance`）。
2. `RegisteredItem` 侧承接 `item_id`；`Item.identifiedItem` 与
   `ScopedIdentifier.identifiedItem` 类型改为 `RegisteredItem`。
3. `IdentifiedItemRepository` 改挂 `RegisteredItem`，JPQL 改写，三个调用点同步；
   `existsById` / `findItemIdById` / `findItemConcreteTypeById` 加用例锁定行为。
4. `V20261009012` 三方言：增 `item_id` 列 → 回填 → 重指 `scoped_identifier` 外键
   → 删旧表，幂等守卫；SQLite 重建 `registered_item` 并保留索引。
5. 改写 `AdministeredItemClosureConformanceTest` 的不变式查询，**判定口径不变**；
   重跑红→绿证据。
6. 更新 `db-baseline` 下三个引用 `identified_item` 的脚本。
7. 冒烟：按标识符反查、条目侧与注册项侧双向读取。

**退出判据**：白名单测试未改仍绿；迁移幂等；脚本中不再出现 `identified_item`。

### 批次 F — 恢复提交关联（第 6 组，需用户确认）

备份 → 重打包重启（空关联态基线）→ 回填 11,182 条 → 实测
`data-elements` / `administered-items` / `workspace` 三端点 → 界面验收。

**退出判据**：三端点亚秒级；参考文档页签可见 WS/T 文档并可增删。

### 批次 G — 提交前检查（第 7 组）

`detect-changes`（无 partial/truncated）→ 后端全量回归无新增 → `openspec validate`
本变更与 `--all` → 归档记录 + 两条 backlog。

## 4. 验证命令

```bash
# 后端目标测试
./mvnw -pl metadata-registry-web -am test -Dtest='AdministeredItemClosureConformanceTest,SubmissionRecord*'

# 后端全量
./mvnw -pl metadata-registry-web -am test

# 前端
cd metadata-registry-ui && npx vitest run && npm run build

# 迁移实测（真实库副本）
python - <<'PY'   # 用 pyodbc ODBC Driver 17，见会话记录
PY

# OpenSpec
openspec validate administered-item-reference-documents-ui
openspec validate --all

# 图谱分析
node .gitnexus/run.cjs detect-changes --scope all --repo .
```

## 5. 风险与应对

| 风险 | 应对 |
|---|---|
| D1 改写后行序与原 JPQL 不一致 | 实现保持 `id` 降序并加用例锁定 |
| `buildItemMeta` 签名变更波及 15+ 调用点 | 新增参数可选且默认空集合 |
| `getIdentifiedItem()` 类型变更波及未知调用点 | 编译期全量检查（类型变更是编译错误） |
| 恢复关联后 `workspace` 仍慢 | 该端点 owner 判定亦经 `sub.registeredItems`，实测后如实记 backlog |
| 迁移在达梦未实测 | `CREATE INDEX` 与表重建语法三方言通用，风险低；沿用惯例记遗留 |
| 真实库操作不可逆 | 每批操作前备份到 `scratch/`；脚本幂等可重跑 |

## 6. 与 tasks.md 的对应

本计划覆盖 `tasks.md` 第 1–5、7 组；第 6 组待用户确认后执行。