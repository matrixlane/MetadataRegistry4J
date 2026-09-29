## Why

`VersionHistoryService` 与 `WorkspaceRollbackService` 的实现与 ISO/IEC 11179-6:2023 字面规则存在两处偏离：

1. 11179-6 4.3.3.1.2 规定"处于 Incomplete 状态的管理项在注册库中不应受版本控制"，但当前实现给所有新建条目（默认注册状态 INCOMPLETE）无条件写入 CREATE 快照到 `version_history`；
2. 11179-6 4.3.3.1.3 规定"处于 Candidate 状态的管理项受版本控制"，11179-6 全文未对"回退"加注册状态门槛，但当前 `WorkspaceRollbackService.ROLLBACKABLE_REGISTRATION_STATUSES` 与 `WorkspaceService.ROLLBACKABLE_REGISTRATION_STATUSES` 仅含 Recorded+，把 Candidate 排除在外。

`administrative-status/spec.md` 第 6 条（回退窗口）把"Recorded 及以上"作为硬约束，与 11179-6 字面也不一致。本次迭代将实现、spec 与标准三者对齐。

## What Changes

- `VersionHistoryService.recordSnapshot` 与 `VersionHistoryService.captureBaselineIfEnteringDraft`：开头加注册状态过滤——`RegistrationState.registration_status == INCOMPLETE` 时早返回；`null state` 视为透传（保护 mock 单测路径）。
- `WorkspaceRollbackService.ROLLBACKABLE_REGISTRATION_STATUSES` 与 `WorkspaceService.ROLLBACKABLE_REGISTRATION_STATUSES`：将 `RegistrationStatus.CANDIDATE` 加入集合；`WorkspaceService.canRollback` 自动随集合更新。
- 数据迁移 `V20260925001`（sqlite/sqlserver/dm 三方言）：按"当前 RegistrationState.registration_status = INCOMPLETE"的 item_id 删除其全部 `version_history` 行；迁移前打印受影响行数。
- 新增 `openspec/specs/version-control/spec.md`：独立承载"何时受版本控制、回退窗口、SnapshotKind 分类"等核心规则。
- `openspec/specs/administrative-status/spec.md` 第 6 条修改：回退窗口从"Recorded 及以上"改为引用 `version-control` 能力，由新 spec 给出 Candidate+ 的字面规则。

## Capabilities

### New Capabilities

- `version-control`：规定管理项何时受版本控制、回退窗口与 SnapshotKind 分类。依据 ISO/IEC 11179-6:2023 4.3.3.1.2 / 4.3.3.1.3 / 4.3.3.1.4 字面表述。

### Modified Capabilities

- `administrative-status`：第 6 条"Recorded 及以上"硬约束改为引用 `version-control` 能力的"回退窗口"节，文字收缩到"管理状态为 DRAFT 或 REJECTED 的归属用户可触发回退（窗口详见 version-control）"。

## Impact

- 代码：`metadata-registry-web` 模块下 `VersionHistoryService`、`WorkspaceRollbackService`、`WorkspaceService`、`ItemAdministrationService` 调用方（9 个 Item Service 的 create/update 路径）；无外部 API 变更。
- 数据库：三方言迁移 `V20260925001`；删除现有 `version_history` 表中归属当前仍为 INCOMPLETE 的 item 的全部快照行；`version_history` 表结构无变更。
- 测试：`VersionHistoryServiceTest` 现有 mock 用例（`null state` 透传）保留；新增 INCOMPLETE 早返回测试用例；`WorkspaceRollbackServiceIntegrationTest` 新增 Candidate + Draft 回退成功用例；既有 Recorded+ 用例继续通过。
- 文档：`docs/superpowers/specs/2026-09-23-workspace-administrative-status-design.md` §4.6 措辞改为引用新 spec。
- UI：`WorkspaceService.canRollback` 自动生效，Candidate + Draft/Rejected 项回退按钮可见。