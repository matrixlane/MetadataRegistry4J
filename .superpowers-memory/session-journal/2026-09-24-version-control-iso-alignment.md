# 2026-09-24 版本控制逻辑对齐 ISO/IEC 11179-6:2023（align-version-control-iso-11179-6）

## 目标

将 MetadataRegistry4j 的版本控制逻辑完全对齐 11179-6 字面规则：

- 11179-6 4.3.3.1.2：Incomplete "shall not be maintained under version control"
- 11179-6 4.3.3.1.3：Candidate "are maintained under version control"
- 11179-6 4.3.3.1.4：Recorded 及更高 "are maintained under version control"

## OpenSpec 流程

`align-version-control-iso-11179-6`（用户已确认提案/设计/tasks 四份产物）：

- proposal.md（4 改动 + 新/改 capability）
- design.md（5 决策 + 风险 + 迁移 + 0 Open Questions）
- tasks.md（6 组 21 任务）
- specs/version-control/spec.md（新增 3 需求：版本控制范围 / SnapshotKind 分类 / 回退窗口）
- specs/administrative-status/spec.md（MODIFIED 第 6 条：回退窗口从 Recorded+ 改为 Candidate+）

`openspec validate --strict` 通过；`openspec status` 报 `isComplete: true`。

## 实现交付

### 后端代码

- `VersionHistoryService.java`：
  - 新增 `import RegistrationStatus`
  - `recordSnapshot(...)` 私有重载：在 `isAuditedType` 守卫之后、`buildSnapshotJson` 之前加 `RegistrationState.registration_status == INCOMPLETE` 早返回（null state 视为透传，保护 mock 单测路径）
  - `captureBaselineIfEnteringDraft(...)`：在 `isAuditedType` 守卫之后、原 adminStatus 判断之前加同样的 INCOMPLETE 早返回
- `WorkspaceRollbackService.ROLLBACKABLE_REGISTRATION_STATUSES`：新增 `RegistrationStatus.CANDIDATE`
- `WorkspaceService.ROLLBACKABLE_REGISTRATION_STATUSES`：同上

### 测试

- `VersionHistoryServiceTest`：新增 2 用例（`recordSnapshot_shouldDoNothing_whenRegistrationStatusIncomplete` + `captureBaselineIfEnteringDraft_shouldDoNothing_whenRegistrationStatusIncomplete`）；全套 0 failures
- `WorkspaceRollbackServiceIntegrationTest`：
  - 调整 `rollbackDraft_shouldRestoreBaselineAndClearStatus`：版本数从 3 改为 2（去除被新规则排除的 CREATE SUBMITTED 快照）
  - 新增 `rollbackCandidateDraft_shouldRestoreBaselineAndKeepCandidateStatus`：Candidate + Draft + 有 BASELINE → 回退成功，注册状态保持 CANDIDATE，写 ROLLED_BACK
  - 新增 `rollbackCandidateRejected_withoutBaseline_shouldClearStatusOnly`：Candidate + Rejected + 无 BASELINE → 仅清 adminStatus，无 ROLLED_BACK
  - 全套 6 用例 0 failures
- **预存问题**：`WorkspaceServiceIntegrationTest` 6+3=9 用例失败已通过 stash/revert 验证为 master 既有破损（与本变更无关），记入 KNOWN_FAILURES 候补；不属于本次工作流阻塞
- **预存编译问题**：`AdministeredItemResponse` record 扩展 22 字段后 4 个测试文件（Admin/DataDict/MetadataSearch × controller + service）未同步，添加 `, null` 末尾参数解开构建阻塞（非本任务范围，但记录）

### 数据迁移 V20260925001（三方言）

- sqlite / sqlserver / dm 各一份 SQL：
  - SQLite / 达梦：子查询 `DELETE FROM version_history WHERE item_id IN (SELECT ai.id FROM administered_item ai JOIN registration_state rs ON rs.id = ai.registration_state_id WHERE rs.registration_status = 'INCOMPLETE')`
  - SQL Server：等价 JOIN 写法
- `scratch/version-control-smoke/verify_migration.py`：构造 INCOMPLETE/RECORDED 项 + version_history 副本库场景，断言迁移后只删 INCOMPLETE 行 → **PASS**
- 真实生产库 `metadata_registry.db` 检查：0 行受影响（项目历史以"系统启动补建"模式启动，主流程无遗留 Incomplete 运行库）

### 文档同步

- `docs/superpowers/specs/2026-09-23-workspace-administrative-status-design.md` §4.6：去掉"用户规则：版本控制内"措辞，改为引用 `openspec/specs/version-control` 的"回退窗口"节并写明字面对齐

### 验证

- `openspec validate align-version-control-iso-11179-6 --strict` → PASS
- 后端目标包测试（VersionHistoryServiceTest + WorkspaceRollbackServiceIntegrationTest + WorkspaceControllerTest + ItemAdministrationServiceTest + RegistrationLifecycleServiceTest）→ BUILD SUCCESS
- 前端 `npx vitest run` → 262 passed
- `gitnexus detect-changes --scope all` → 10 files / 15 symbols / 10 processes / 无 partial 无 truncated（risk=high 仅为本变更涉及 VersionHistoryService 关键路径的合理评级，不是真"风险"）
- 前端 `npm run build` → 27 个 TS 错误（pre-existing，与本变更无关，未改动前端文件）

## 待 continue-review 阶段

按 OpenSpec + Superpowers 工作流：实现 + 验证完成，停下等 `continue-review` 进代码评审；评审通过后 `continue-archive` 触发 OpenSpec archive 流程（version-control 合并入主 spec + administrative-status MODIFIED 落地）。

## 风险/限制

- `WorkspaceServiceIntegrationTest` 9 个预存失败需作为后续 backlog 单独处理（不在本次工作流范围内）
- 前端 27 个 pre-existing TS 错误不在本次范围
- 数据迁移真实生产库 0 行受影响；如未来存在遗留 Incomplete + version_history，需在迁移前手动评估影响