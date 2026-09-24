# Proposal: qualified-downgrade-approval

## Why

既有默认审核点只覆盖向上迁移，与 ISO/IEC 11179-6:2023 D.4.2/D.4.3 不符：标准明确要求 Qualified 及以上的管理项变更到 "Retired" / "Superseded" 须经 control committee 审议（registrar 执行）；且 D.2.9 表明 Superseded → Retired 同样须委员会批准。当前实现中这些降级迁移 `requires_approval = false`，REGISTRAR 可直接执行，存在治理缺口。Recorded → Retired/Superseded 标准明确 submitter 无需审查，应保持直接执行。

## What Changes

- **BREAKING（治理语义）**：`QUALIFIED→RETIRED`、`STANDARD→RETIRED`、`PREFERRED_STANDARD→RETIRED`、`QUALIFIED→SUPERSEDED`、`STANDARD→SUPERSEDED`、`PREFERRED_STANDARD→SUPERSEDED`、`SUPERSEDED→RETIRED` 共 7 条迁移纳入默认审核点（种子规则 `requires_approval = true`），须经所属注册机构 Registrar 批准达 quorum 后生效，直推被拒绝（1120）。
- `RECORDED→RETIRED`、`RECORDED→SUPERSEDED` 保持无需审核，可直接执行（对齐 D.4.2/D.4.3 "Recorded 级 submitter 无需审查"）。
- `CANDIDATE→RECORDED` 的治理加严审核点**撤销**（2026-09-19 修订）：经 4.4 c) 原文复核，"Submitters or stewards can progress administered items in the 'Candidate' status to the 'Recorded' registration status by completing all mandatory metadata attributes"——标准最低要求无需批准，恢复为直接执行（强制属性完备校验照旧）。D.2.3 建议的 steward 复审/registrar 筛查属建议性实践，本系统以 REGISTRAR 角色门槛近似（无独立 Submitter/Steward 角色）。
- `→SUPERSEDED` 的执行方式从"仅 ADMIN 直推"变更为"Registrar quorum 批准生效"（quorum ≈ control committee 审议；批准生效路径跳过角色矩阵，既有 `applyTransition` 语义不变）。
- 既有注册机构的规则表在下次 `ensureSeed` 时自动收敛（7 条 Qualified+ 降级规则 `requires_approval` 翻转为 `true`；旧库 `CANDIDATE→RECORDED` 加严规则翻转为 `false`；displayName 非空的定制规则不被触碰）。
- 无归属注册机构的条目：默认审核集合内迁移按 M1 守卫拒绝直推，提示先归属注册机构；`CANDIDATE→RECORDED` 不在集合内，保持可直推。
- 既有测试更新：断言 Qualified+ 降级可直推的用例改为期望 1120 或改走批准路径；治理加严相关用例按 4.4 c) 改为期望直推。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `registration-process`: 以 REMOVED + ADDED 替换"标准默认审核点"需求——新需求"标准默认审核点（10 处）"：3 条 4.4 d) 向上迁移 + 7 条 D.4.2/D.4.3/D.2.9 Qualified+ 降级迁移；`CANDIDATE→RECORDED` 按 4.4 c) 恢复为无需审核（撤销此前的治理加严）。

## Impact

- 代码：`RegistrationLifecycleService.DEFAULT_APPROVAL_TRANSITIONS` 及相关 javadoc/注释；`RegistrationProcessService` 注释表述。
- API 行为：`POST .../execute` 对 7 条 Qualified+ 降级迁移返回 1120、`CANDIDATE→RECORDED` 恢复直推；`.../approve` 成为降级迁移的生效路径；`availableTransitions` 视图相应更新 `canExecute`/`canApprove`。
- 数据：各注册机构 `registration_transition_rule` 种子行自动收敛（无需手工迁移脚本）。
- 前端：执行/批准按钮由服务端 `canExecute`/`canApprove` 驱动，无需改动。
- 测试：`RegistrationLifecycleServiceTest`、`RegistrationProcessServiceTest`、`RegistrationProcessControllerTest`。
