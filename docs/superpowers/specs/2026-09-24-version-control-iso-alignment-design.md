# 版本控制逻辑对齐 ISO/IEC 11179-6:2023 设计

日期：2026-09-24
状态：待用户确认
关联变更：（待定，OpenSpec change 名建议 `align-version-control-iso-11179-6`）

## 1. 背景与目标

本项目 `version_history` 表与 `VersionHistoryService` 实现的"版本控制"语义与 ISO/IEC 11179-6:2023 字面规则存在两处偏离。本次迭代将实现与字面规则完全对齐。

字面规则出处（Milvus 检索 `anythingllm_metadata_registry.ISO_IEC_11179-6-2023.pdf`，原文逐条核对）：

- "When an administered item has the 'Incomplete' status, ... **An administered item in the status of 'Incomplete' in the registry shall not be maintained under version control.**"
- "When an administered item has the 'Candidate' status, ... **Administered items in the 'Candidate' status are maintained under version control.**"
- "...the submitter may request the registration status of 'Recorded' at any time. **Administered items in 'Recorded' registration status or higher are maintained under version control.**"

## 2. 用户已确认的决策

| # | 决策 | 结论 |
|---|---|---|
| 1 | 数据迁移策略 | **A. 数据清洗**：随本次变更加一次性 SQL（V20260925001，三方言），删除 item_id 对应当前仍为 INCOMPLETE 的全部版本快照。删除前一行日志记录受影响行数。 |
| 2 | spec 归属 | **A. 新增 `openspec/specs/version-control/spec.md`**：独立 spec 承载"何时受版本控制、回退窗口、snapshot kind 分类"等核心规则；workspace 与 registration-status spec 通过引用复用其规则。 |
| 3 | 标准依据 | ISO/IEC 11179-6:2023 4.3.3.1.2（Incomplete 不入版本控制）、4.3.3.1.3（Candidate 受版本控制）、4.3.3.1.4（Recorded 及更高受版本控制）。Milvus 检索原文已逐字校对。 |
| 4 | 需保留的行为 | BASELINE/DRAFT_EDIT/SUBMITTED/ROLLED_BACK 四类 kind 分类逻辑；`captureBaselineIfEnteringDraft` 现有 admin status 触发条件；Candidate 与 Recorded+ 上的 BASELINE 捕获行为不变。 |
| 5 | 测试覆盖范围 | 9 个 Item Service 的 create/update、WorkspaceController、VersionHistoryService 单元测试、WorkspaceRollbackServiceIntegrationTest、WorkspaceServiceIntegrationTest、端到端冒烟（scratch 副本库模式）。 |

## 3. 总体设计

### 3.1 实施切入点

```
                    ┌─────────────────────────────────┐
                    │  ItemAdministrationService      │
                    │  .applyOrUpdate()               │
                    │  （设置 RegistrationState       │
                    │   INCOMPLETE 初始）             │
                    └────────────┬────────────────────┘
                                 │
                                 ▼
        ┌────────────────────────────────────────────┐
        │ ItemService.create / update                │
        │ （9 个：DataElement、ValueDomain、…）      │
        │   ↓ create 后立即：recordSnapshot("CREATE")│
        │   ↓ update 前：captureBaselineIfEnteringDraft│
        │   ↓ update 后：recordSnapshot("UPDATE")    │
        └────────────┬───────────────────────────────┘
                     │
                     ▼
        ┌────────────────────────────────────────────┐
        │ VersionHistoryService                      │
        │  .recordSnapshot(item, changeType, dto)    │
        │   ↑ 新增：RegistrationState == INCOMPLETE │
        │     时早返回（null state 视为透传）        │
        │  .captureBaselineIfEnteringDraft(...)     │
        │   ↑ 新增：RegistrationState == INCOMPLETE │
        │     时早返回（null state 视为透传）        │
        └────────────┬───────────────────────────────┘
                     │
                     ▼
        ┌────────────────────────────────────────────┐
        │ version_history 表                          │
        │ （不再有 INCOMPLETE 行；后续 Candidate+ 行 │
        │  的 version_number 自 1 开始重新计数）      │
        └────────────────────────────────────────────┘
```

### 3.2 现状代码定位

- `VersionHistoryService.recordSnapshot`（`metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/VersionHistoryService.java:75-128`）：唯一公共落点。
- `VersionHistoryService.captureBaselineIfEnteringDraft`（同文件 `:137-150`）：BASELINE 捕获入口。
- 9 个 Item Service 的 `create` 路径模板（以 `DataElementService.java:47-53` 为代表）：`repository.save(saved) → recordSnapshot(saved, "CREATE", toResponse(saved))`。
- `WorkspaceRollbackService.ROLLBACKABLE_REGISTRATION_STATUSES`（`WorkspaceRollbackService.java:42-49`）：仅 Recorded+。
- `WorkspaceService.ROLLBACKABLE_REGISTRATION_STATUSES`（`WorkspaceService.java:54-61`）：同上。
- `WorkspaceService.canRollback`（`WorkspaceService.java:278-287`）：用同一集合判定 UI 按钮可见性。

### 3.3 详细变更

#### 3.3.1 INCOMPLETE 过滤（对齐 11179-6 4.3.3.1.2）

**变更点 1**：`VersionHistoryService.recordSnapshot`（`recordSnapshot(item, changeType, responseDto, kind)` 私有重载）

在 `isAuditedType(itemType)` 校验之后、`buildSnapshotJson` 之前插入：

```java
RegistrationState state = getRegistrationState(item);
if (state != null && state.getRegistrationStatus() == RegistrationStatus.INCOMPLETE) {
    return; // 标准 11179-6 4.3.3.1.2：Incomplete 不入版本控制
}
```

`null state` 视为透传：现有 `VersionHistoryServiceTest` 中以 `new DataElement()` 直接构造的 mock 路径不携带 RegistrationState，需保留其行为以免单测大面积回归。

**变更点 2**：`VersionHistoryService.captureBaselineIfEnteringDraft`

在 `isAuditedType(itemType)` 校验之后、原 adminStatus 判断之前插入同样的 INCOMPLETE 早返回。

#### 3.3.2 回退窗口放宽（对齐 11179-6 4.3.3.1.3）

**变更点 3**：`WorkspaceRollbackService.ROLLBACKABLE_REGISTRATION_STATUSES`

```java
private static final Set<RegistrationStatus> ROLLBACKABLE_REGISTRATION_STATUSES = Set.of(
        RegistrationStatus.CANDIDATE,           // 新增：标准 11179-6 4.3.3.1.3
        RegistrationStatus.RECORDED,
        RegistrationStatus.QUALIFIED,
        RegistrationStatus.STANDARD,
        RegistrationStatus.PREFERRED_STANDARD,
        RegistrationStatus.SUPERSEDED,
        RegistrationStatus.RETIRED
);
```

**变更点 4**：`WorkspaceService.ROLLBACKABLE_REGISTRATION_STATUSES`：同变更点 3。

`canRollback` 与 `validateRollbackState` 复用同一集合，无需额外变更；UI 按钮显示条件自动随集合更新。

**回退降级路径**：`WorkspaceRollbackService.rollback` 第 81-87 行（`if (baselineOpt.isEmpty())` 兜底分支）保持不变——Candidate + Rejected 无 BASELINE 时仍走"仅清 adminStatus"降级。

#### 3.3.3 数据迁移（决策 1 = A）

**变更点 5**：V20260925001，三方言迁移脚本（`metadata-registry-web/src/main/resources/db/migration/{sqlite,sqlserver,dm}/V20260925001__Purge_incomplete_version_snapshots.sql`）。

逻辑（语义一致，方言可读性优先）：

- SQLite（无 JSON 函数可用）：

  ```sql
  DELETE FROM version_history
  WHERE item_id IN (
      SELECT ai.item_id
      FROM administered_item ai
      JOIN registration_state rs ON rs.id = ai.registration_state_id
      WHERE rs.registration_status = 'INCOMPLETE'
  );
  ```

- SQL Server：

  ```sql
  DELETE vh
  FROM version_history vh
  JOIN administered_item ai ON ai.item_id = vh.item_id
  JOIN registration_state rs ON rs.id = ai.registration_state_id
  WHERE rs.registration_status = 'INCOMPLETE';
  ```

- 达梦：

  ```sql
  DELETE FROM version_history
  WHERE item_id IN (
      SELECT ai.item_id
      FROM administered_item ai
      INNER JOIN registration_state rs ON rs.id = ai.registration_state_id
      WHERE rs.registration_status = 'INCOMPLETE'
  );
  ```

迁移前打印受删除影响行数（通过 Flyway 占位符或前后端日志记录，不在本迁移内做断言）。

#### 3.3.4 新 spec（决策 2 = A）

**变更点 6**：新增 `openspec/specs/version-control/spec.md`。

需求分节：

1. **版本控制范围**：明确 Incomplete 不入版本控制；Candidate+ 受版本控制。引用 11179-6 4.3.3.1.2 / 1.3 / 1.4 原文。
2. **SnapshotKind 分类**：BASELINE / DRAFT_EDIT / SUBMITTED / ROLLED_BACK 四类的写入触发条件。
3. **回退窗口**：Candidate+ 且 adminStatus ∈ {DRAFT, REJECTED} 时可回退；Rejected 且无 BASELINE 时仅清 adminStatus。
4. **数据可见性**：版本历史 UI 列表回退目标为最近一条 BASELINE。

引用关系：

- `workspace/spec.md` 中"行级操作"提及"回退"时，引用本 spec 的"回退窗口"节。
- `registration-status/spec.md` 中关于版本控制的描述统一引用本 spec。

#### 3.3.5 设计文档同步

**变更点 7**：`docs/superpowers/specs/2026-09-23-workspace-administrative-status-design.md` §4.6

将原"仅注册状态 ∈ {Recorded, …} 时可回退（**用户规则：版本控制内**）"改为：

> "回退窗口与 11179-6 4.3.3.1.3 字面一致：注册状态 ∈ {Candidate, Recorded, Qualified, Standard, PreferredStandard, Superseded, Retired} 且 adminStatus ∈ {DRAFT, REJECTED}。详见 `openspec/specs/version-control` 的'回退窗口'需求。"

## 4. 边界与异常

- **null state 透传**：mock 单测的 `new DataElement()` 不携带 RegistrationState；过滤逻辑只对显式 INCOMPLETE 早返回，null 仍走正常路径。
- **rollback 期间不重复落 BASELINE**：`setRestoreInProgress(true)` 期间，被回退项注册状态 ∈ Recorded+（回退前置校验保证），不会命中新增的 INCOMPLETE 早返回，无需额外分支。
- **Registry Context 启动条目**：`RegistryContextBootstrapService` 写入的 RegistryContext 条目初始状态为 Recorded（`RegistryContextBootstrapService.java:130`），不会命中 INCOMPLETE 过滤。
- **联邦并入条目**：`MergeExecutor.recordSnapshot(local.getItem(), "MERGE")` 时，local 已是 Registered 项，状态非 INCOMPLETE，不受影响。
- **历史 CREATE 快照**：当前 `meta.kind` 推导里 `"CREATE".equals(changeType)` → `SUBMITTED`，本次不调整（仅是分类标签，与"是否落盘"是两件事）。

## 5. 待确认事项

1. 决策 1/2 用户已确认（A/A），无悬而未决项。
2. 数据迁移是否需要 Flyway `outOfOrder` 标志——本迭代序号递增，默认顺序应用即可，无需特殊处理。
3. 历史 Incomplete 批次删除是否会触发外键约束——`version_history` 表无外键引用（已核对 `V20260513009__Create_version_history_table.sql`），DELETE 无级联风险。

## 6. 验证标准（VERIFICATION_BASELINE.md 已立条目，本迭代复用）

- 后端：`./mvnw -pl metadata-registry-web -am test -Dtest='VersionHistoryServiceTest,WorkspaceRollbackServiceIntegrationTest,WorkspaceServiceIntegrationTest,*ServiceTest' -DfailIfNoTests=false` → 0 failures。
- 前端：`cd metadata-registry-ui && npx vitest run && npm run build` → 258/258 + build 通过。
- 数据迁移：`scratch/` 副本库执行 V20260925001，统计受删除行数；migration log 留痕。
- 端到端冒烟：`scratch/version-control-smoke/` 新增脚本，覆盖：
  1. 创建 DataElement → 立即查 `version_history` 应为 0 行（Incomplete 不入）；
  2. Request Candidate → adminStatus 清空 → 查 `version_history` 仍为 0 行；
  3. 编辑 → adminStatus=DRAFT → 查 `version_history` 应有 1 条 BASELINE + 1 条 DRAFT_EDIT；
  4. 提交 → adminStatus 清空 → 查 `version_history` 应有 3 条；
  5. 再次编辑 → BASELINE + DRAFT_EDIT → 查应 5 条；
  6. 验证 `WorkspaceService.canRollback` 在 Candidate+Draft 状态下返回 true。
- `bunx gitnexus@latest detect-changes --scope all --repo .` → 无 partial/truncated。
- 收尾 OpenSpec `openspec status --change "align-version-control-iso-11179-6" --json` → all artifacts ready。