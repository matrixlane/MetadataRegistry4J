# 实现计划：版本控制逻辑对齐 ISO/IEC 11179-6:2023

日期：2026-09-24
关联 OpenSpec change：`align-version-control-iso-11179-6`
关联设计文档：`docs/superpowers/specs/2026-09-24-version-control-iso-alignment-design.md`

## 1. 实施范围

落地 5 个代码改动点 + 1 个数据迁移 + 1 个文档同步。详细背景见 design.md；本计划仅列落地步骤与每步验证。

## 2. 实施步骤（按依赖顺序）

### 阶段 A：后端代码 + 单测（TDD）

#### A.1 `VersionHistoryService.recordSnapshot` INCOMPLETE 早返回

**文件**：`metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/VersionHistoryService.java`

定位：私有重载 `recordSnapshot(Item item, String changeType, Object responseDto, SnapshotKind kind)`（约 92 行）。在 `isAuditedType(itemType)` 守卫之后、`buildSnapshotJson` 之前插入：

```java
RegistrationState state = getRegistrationState(item);
if (state != null && state.getRegistrationStatus() == RegistrationStatus.INCOMPLETE) {
    return; // ISO/IEC 11179-6:2023 4.3.3.1.2
}
```

`null state` 视为透传（保护 mock 单测路径）。

**测试先行**：在 `VersionHistoryServiceTest` 新增用例 `recordSnapshot_shouldDoNothing_whenRegistrationStatusIncomplete`，mock DataElement 携带 `mock RegistrationState.setRegistrationStatus(INCOMPLETE)`，断言 `repository.save` 不被调用。

**验证**：`./mvnw -pl metadata-registry-web test -Dtest='VersionHistoryServiceTest' -DfailIfNoTests=false` → 0 failures（含既有 mock 用例）。

#### A.2 `VersionHistoryService.captureBaselineIfEnteringDraft` INCOMPLETE 早返回

**文件**：同上。

定位：`captureBaselineIfEnteringDraft(Item item, Object responseDto)`（约 137 行）。在 `isAuditedType(itemType)` 守卫之后、原 adminStatus 判断之前插入同样过滤。

**测试先行**：在 `VersionHistoryServiceTest` 新增 `captureBaselineIfEnteringDraft_shouldDoNothing_whenRegistrationStatusIncomplete`。

**验证**：同 A.1。

#### A.3 `WorkspaceRollbackService.ROLLBACKABLE_REGISTRATION_STATUSES` 加 CANDIDATE

**文件**：`metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/WorkspaceRollbackService.java`

定位：常量定义（约 42 行）。在集合字面量开头加入 `RegistrationStatus.CANDIDATE,`。

**测试先行**：在 `WorkspaceRollbackServiceIntegrationTest` 新增 3 个用例：
1. `rollback_shouldSucceed_whenCandidateAndDraft_withBaseline` — 期望：恢复 BASELINE、写 ROLLED_BACK、adminStatus 清空、注册状态保持 CANDIDATE。
2. `rollback_shouldJustClearAdminStatus_whenCandidateAndRejected_withoutBaseline` — 期望：adminStatus 清空，无 ROLLED_BACK 写入，主表不变。
3. 既有 Recorded+ 用例继续通过（无修改）。

**验证**：`./mvnw -pl metadata-registry-web test -Dtest='WorkspaceRollbackServiceIntegrationTest' -DfailIfNoTests=false` → 0 failures。

#### A.4 `WorkspaceService.ROLLBACKABLE_REGISTRATION_STATUSES` 加 CANDIDATE + canRollback 自动生效

**文件**：`metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/WorkspaceService.java`

定位：常量定义（约 54 行）。在集合字面量开头加入 `RegistrationStatus.CANDIDATE,`。

**测试先行**：在 `WorkspaceServiceIntegrationTest` 或 `WorkspaceControllerTest` 中新增用例 `canRollback_shouldReturnTrue_whenCandidateAndDraft`。如已有针对 `canRollback` 的用例集合，扩展其一覆盖 Candidate。

**验证**：相关测试 0 failures。

### 阶段 B：数据迁移（不依赖 TDD，可并行起草）

#### B.1 三方言 V20260925001

**文件**：
- `metadata-registry-web/src/main/resources/db/migration/sqlite/V20260925001__Purge_incomplete_version_snapshots.sql`
- `metadata-registry-web/src/main/resources/db/migration/sqlserver/V20260925001__Purge_incomplete_version_snapshots.sql`
- `metadata-registry-web/src/main/resources/db/migration/dm/V20260925001__Purge_incomplete_version_snapshots.sql`

SQL 见 design.md §3.3.3。

**验证**：Flyway 启动 sqlite 真实库副本（`scratch/` 副本库模式）应用迁移，记录受影响行数；预期 0（项目历史以"系统启动补建"模式启动，主流程没有遗留 Incomplete 的运行库）。

### 阶段 C：文档同步

#### C.1 design.md §4.6 措辞更新

**文件**：`docs/superpowers/specs/2026-09-23-workspace-administrative-status-design.md`

定位：§4.6 第 144 行。把"仅注册状态 ∈ {Recorded, …} 时可回退（**用户规则：版本控制内**）"改为：

> "回退窗口与 11179-6 4.3.3.1.3 字面一致：注册状态 ∈ {Candidate, Recorded, Qualified, Standard, PreferredStandard, Superseded, Retired} 且 adminStatus ∈ {DRAFT, REJECTED}。详见 `openspec/specs/version-control` 的'回退窗口'需求。"

**验证**：grep "用户规则" 在设计文档中无残留命中。

### 阶段 D：端到端冒烟

#### D.1 `scratch/version-control-smoke/` 冒烟脚本

**验证路径**（6 个断言）：
1. 创建 DataElement 立即查 `version_history` 应为 0 行
2. Request Candidate 后查仍为 0 行
3. 编辑后查应 1 条 BASELINE + 1 条 DRAFT_EDIT
4. 提交后再编辑应累计 5 条
5. `WorkspaceService.canRollback` 在 Candidate+Draft 状态下返回 true
6. Candidate + Draft 执行回退成功，主表恢复，adminStatus 清空

**验证**：脚本全断言 PASS（脚本模式同 `scratch/registry-context-smoke/smoke.sh`，真实库副本 + classpath）。

### 阶段 E：最终验证

```
1. openspec validate align-version-control-iso-11179-6 --strict
2. ./mvnw -pl metadata-registry-web -am test -DfailIfNoTests=false
3. cd metadata-registry-ui && npx vitest run && npm run build
4. bunx gitnexus@latest detect-changes --scope all --repo .
5. openspec archive align-version-control-iso-11179-6
```

## 3. 验证基线（VERIFICATION_BASELINE.md）

复用既有 `verification-2026-09-13-targeted-plus-smoke` 规则：
- 目标包测试 0 failures
- 前端 vitest + build
- 真实握手/冒烟（不走假 server）
- 收尾 detect_changes 无 partial/truncated

## 4. 风险与回退

- **回退路径**：revert PR 即可，迁移 V20260925001 仍存在（Flyway 不会重复执行）；紧急情况写 V20260925002 反向 INSERT 历史快照（仅临时使用）。
- **运行时风险**：测试先写先红，代码改动后变绿；任何步骤 red 则立即停下定位。
- **数据风险**：迁移在事务中执行；上线前在 scratch 副本库跑通。

## 5. 接续步骤

完成所有阶段后，停下来请求 `continue-review`，由用户走代码评审；评审通过后再 `continue-archive`。