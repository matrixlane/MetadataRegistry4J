## Context

`VersionHistoryService` 与 `WorkspaceRollbackService` 当前实现与 ISO/IEC 11179-6:2023 字面规则有两处偏离：`recordSnapshot` 无条件落库（即使 `RegistrationState.registration_status = INCOMPLETE`），`ROLLBACKABLE_REGISTRATION_STATUSES` 仅含 Recorded+。变更动机详见 `proposal.md`。

涉及代码（`metadata-registry-web`）：

- `service/VersionHistoryService.java`：唯一公共落点（`recordSnapshot` 重载 + `captureBaselineOpenEnteringDraft`）。
- 9 个 Item Service 的 `create` 模板：`repository.save(saved) → recordSnapshot(saved, "CREATE", toResponse(saved))`；`update` 模板：`captureBaselineOpenEnteringDraft → applyRequest → save → recordSnapshot(saved, "UPDATE", ...)`。
- `service/WorkspaceRollbackService.java`：回退服务，唯一校验点 `ROLLBACKABLE_REGISTRATION_STATUSES`。
- `service/WorkspaceService.java`：UI 标志 `canRollback` 复用同一集合。
- `db/migration/{sqlite,sqlserver,dm}/V20260925001__Purge_incomplete_version_snapshots.sql`：新迁移，三方言。

现有测试：`VersionHistoryServiceTest`（mock `DataElement` 不携带 RegistrationState）、`WorkspaceRollbackServiceIntegrationTest`、`WorkspaceServiceIntegrationTest`。

## Goals / Non-Goals

**Goals:**

- `version_history` 表不再积累 INCOMPLETE 注册状态的快照；新规则生效后所有写入均为 Candidate+ 状态。
- 工作空间回退窗口从 Recorded+ 放宽到 Candidate+；UI 按钮可见性自动随 `canRollback` 集合更新。
- 现有 INCOMPLETE 历史快照通过一次性迁移清洗，与新规则一致。
- `administrative-status/spec.md` 第 6 条硬约束改为引用新 `version-control` spec，避免规则重复。

**Non-Goals:**

- 不修改 SnapshotKind（`BASELINE` / `SUBMITTED` / `DRAFT_EDIT` / `ROLLED_BACK`）分类逻辑。
- 不修改 `captureBaselineOpenEnteringDraft` 既有 adminStatus 触发条件（仅在 `isAuditedType` 之后追加 INCOMPLETE 早返回）。
- 不引入 `version_history` 表结构变更。
- 不改 `RegistrationStatus` 枚举与持久化格式。
- 不动 11179-6 表 1 之外的注册状态语义（如 Initial → Qualified 推进路径）。

## Decisions

### Decision 1: 过滤点选 `VersionHistoryService` 而非 9 个 Item Service

- 选择：在 `recordSnapshot` 与 `captureBaselineOpenEnteringDraft` 入口处统一加 INCOMPLETE 早返回。
- 备选：在每个 Item Service 的 `create` 末尾加 `if (saved.getRegistrationState().getRegistrationStatus() != INCOMPLETE) recordSnapshot(...)`，9 处重复。
- 理由：单一落点更易维护；与既有 `isAuditedType` 守卫同层（也是入口过滤）；9 处重复易遗漏（如未来新增 Item 类型或联邦并入路径）。

### Decision 2: `null state` 透传而非一律视作 INCOMPLETE

- 选择：当 `RegistrationState` 为 `null` 时不阻断 `recordSnapshot`。
- 备选：把 null state 一律视作 INCOMPLETE 并跳过。
- 理由：`VersionHistoryServiceTest` 大量用例以 `new DataElement()` 直接构造 mock，不携带 RegistrationState（`getIdentifiedItem() instanceof AdministeredItem` 为 `false`）；若 null 状态视为需跳过，所有现有 mock 测试会被连累失败。`null state` 在生产代码中仅出现在 `recordSnapshot(item, changeType)` 调用的非持久化路径或被 mock 测试模拟的场景，安全性可接受。

### Decision 3: 回退集合加入 `CANDIDATE` 而非新增分支

- 选择：在 `WorkspaceRollbackService.ROLLBACKABLE_REGISTRATION_STATUSES` 与 `WorkspaceService.ROLLBACKABLE_REGISTRATION_STATUSES` 两处同一行常量集合加入 `RegistrationStatus.CANDIDATE`。
- 备选：在 `WorkspaceService.canRollback` 与 `WorkspaceRollbackService.validateRollbackState` 各加一行 `if (registrationStatus == CANDIDATE) allowed = true;` 分支。
- 理由：单一集合即所有回退窗口的真相源，与既有模式一致；无需改动 `canRollback` 主逻辑。

### Decision 4: 数据迁移仅按当前 `RegistrationState.registration_status = INCOMPLETE` 的 item 过滤

- 选择：迁移 SQL 子查询为"当前 RegistrationState 仍为 INCOMPLETE 的 item_id"，按此集合删除 `version_history` 行。
- 备选：解析 `snapshot_json.meta.registrationStatus` 字符串（SQLite 无原生 JSON 函数；需方言差异化）。
- 理由：当前 `RegistrationState` 是单行更新（无 `previous_state` 链时）的快照；只要当前 INCOMPLETE，则其全部历史快照都未被标准覆盖。可读性优先。

### Decision 5: `version-control` 独立 spec 而非合并到 `registration-status`

- 选择：新增 `openspec/specs/version-control/spec.md`。
- 备选：合并到 `registration-status/spec.md` 或 `administrative-status/spec.md`。
- 理由：`version-control` 跨注册状态、跨 adminStatus、跨多种写入路径（CREATE / UPDATE / DELETE / MERGE / ROLLBACK），单一关注点便于未来扩展（如版本号策略、IRDI VI 字段）独立演化。

## Risks / Trade-offs

- [现有 mock 测试在 null state 透传下可能掩盖回归] → `VersionHistoryServiceTest` 新增 1 条带 mock RegistrationState 的 INCOMPLETE 早返回用例。
- [回退窗口放宽后，Candidate + Rejected + 无 BASELINE 的组合降级路径需验证] → `WorkspaceRollbackServiceIntegrationTest` 新增 Candidate + Rejected 无 BASELINE 仅清 adminStatus 用例。
- [数据迁移误删需要恢复的快照] → 迁移在事务中执行（Flyway 单文件默认非事务方言差异：达梦默认非事务；项目历史迁移经验 `registry-context-bootstrap` 等已对达梦事务做过实测）；上线前在 `scratch/` 副本库执行并核对受影响行数；归档时记录受影响行数供事后审计。
- [新 spec 与 `administrative-status/spec.md` 第 6 条引用关系需保持一致] → 归档前 `openspec validate` 校验；引用文字与 spec id 完全一致。

## Migration Plan

部署步骤：

1. 后端代码：实现 `VersionHistoryService` INCOMPLETE 早返回 + `ROLLBACKABLE_REGISTRATION_STATUSES` 加入 `CANDIDATE`；通过 `./mvnw -pl metadata-registry-web test`。
2. 数据迁移 `V20260925001`：随下一次 Flyway migrate 自动应用；受影响行数通过应用日志记录。
3. 端到端冒烟：scratch 副本库跑完整 Incomplete→Candidate→回退路径。
4. 前端：现有 WorkspaceView UI 自动随 `canRollback` 集合更新，无需新增按钮组件；无前端改动。

回滚策略：

- 代码回滚：revert PR 即可，迁移 V20260925001 仍存在（Flyway 不会重复执行）；后续如需回退迁移可手动写一个反向 V20260925002 INSERT 历史快照（仅在紧急情况下）。
- 数据回滚：V20260925001 删除前，应用日志记录受影响 `version_history.id` 列表，运维可基于此人工恢复（实际不可逆，仅在迁移前未应用成功时可回退）。

## Open Questions

无。所有可推迟问题已在 Q1/Q2 澄清阶段解决（数据清洗策略与 spec 归属）。`scratch/` 副本库受删除行数预期为 0（项目历史以"系统启动补建"模式启动，主流程没有遗留 Incomplete 的运行库），如发现非零需在归档前追加"补充迁移"任务。