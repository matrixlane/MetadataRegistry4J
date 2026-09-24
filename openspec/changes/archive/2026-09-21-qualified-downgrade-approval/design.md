# Design: qualified-downgrade-approval

## Context

默认审核点的唯一真源是 `RegistrationLifecycleService.DEFAULT_APPROVAL_TRANSITIONS`（`metadata-registry-web/src/main/java/.../service/RegistrationLifecycleService.java:382`），为按迁移对判定的字符串集合。消费方：

- `RegistrationProcessService.ensureSeed`（播种与逐次调和 `requires_approval`，`:137-141,178-179`）
- `RegistrationProcessService` 无归属注册机构条目的静态底线回退（`:257-258`、`:342`）与视图组装
- `RegistrationLifecycleService.transition` 的 M1 守卫（`:221-227`）

批准生效路径 `RegistrationProcessService.approve()` → `RegistrationLifecycleService.applyTransition()` 跳过交互角色校验（授权由 quorum 表达），既有机制可直接复用。superpowers 设计全文见 `docs/superpowers/specs/2026-09-19-qualified-downgrade-approval-design.md`。

## Goals / Non-Goals

**Goals:**

- Qualified 及以上 → Retired / Superseded（含 Superseded→Retired）默认须经注册机构批准生效（共 10 处默认审核点：3 条向上 + 7 条降级）。
- Recorded → Retired / Superseded 保持直接执行；CANDIDATE→RECORDED 按 4.4 c) 直接执行（治理加严已撤销）。
- 旧库规则行经 `ensureSeed` 自动收敛，无手工数据迁移。

**Non-Goals:**

- 不引入 administrative status 层（"Provisionally Retired" 等，WI-009-2 正交维度，另立变更）。
- 不建模 control committee 实体；以 Registrar quorum 审议近似（ALL_REGISTRARS + ANY_ONE 为默认种子）。
- 不新增/修改 API 端点与前端代码。

## Decisions

**D1：扩充 `DEFAULT_APPROVAL_TRANSITIONS`，而非按目标状态判定。**

最终集合共 10 条迁移对，两类来源：

- 4.4 d) 向上迁移 3 条：`RECORDED→QUALIFIED`、`QUALIFIED→STANDARD`、`STANDARD→PREFERRED_STANDARD`；
- D.4.2/D.4.3 Qualified 及以上降级 + D.2.9 `SUPERSEDED→RETIRED` 共 7 条。

保留按迁移对语义（现有注释已强调"非目标状态"），`RECORDED→RETIRED` / `RECORDED→SUPERSEDED` 不受降级审核影响。替代方案（按目标状态判定）会误伤 Recorded 级迁移，标准明确其无需审查。

**D5：撤销 CANDIDATE→RECORDED 治理加严审核点（2026-09-19 修订，用户裁定"与标准对齐"）。**

4.4 c) 原文授予 submitter/steward 晋升权："Submitters or stewards **can** progress administered items in the 'Candidate' status to the 'Recorded' registration status **by completing all mandatory metadata attributes**"；4.4 d) 的批准要求明确限定 "Qualified or higher"。据此将该迁移移出默认审核集合，恢复直推（强制属性校验照旧）。D.2.3 建议流程中的 steward 复审/registrar 筛查为建议性（annex）实践且非前置批准闸门，本系统无独立 Submitter/Steward 角色，以 REGISTRAR 角色门槛近似其审查效果。旧库中该规则的 `requires_approval=true` 由 `ensureSeed` 自动归位为 `false`（displayName 非空的定制规则不触碰）。

**D2：`→SUPERSEDED` 由"ADMIN 直推"改为"quorum 批准生效"。**

原 `ROLE_PERMISSIONS` 中 `→SUPERSEDED` 限 ADMIN（"重大管理决策"）。纳入审核点后直推路径对所有角色关闭（含 ADMIN），生效由该机构合格 Registrar 批准达成。quorum 审议 ≈ control committee presentation（D.4.3），批准生效路径跳过角色矩阵（既有 `applyTransition` 设计）。`ROLE_PERMISSIONS` 的 ADMIN-only 行保留——静态底线路径（无归属注册机构条目）角色校验仍生效。替代方案 B（保留 ADMIN 直推、仅审 Retired）造成 D.4.2/D.4.3 语义不对称，已否决。

**D3：数据收敛依赖 `ensureSeed` 既有调和逻辑，不写迁移脚本。**

`ensureSeed` 已在每次访问时把非定制规则（displayName 为空）的 `requires_approval` 重算为 `isDefaultApprovalTransition(from, to)`。扩充集合后旧库 7 条规则自动翻转；定制规则（displayName 非空）按既有约定不触碰。

**D4：M1 守卫语义自动延伸。**

无归属注册机构的条目对 7 条新审核迁移拒绝直推（"请先为条目归属注册机构"），与既有 4 条审核迁移行为一致，无需新增分支。

## Risks / Trade-offs

- [既有集成/演示数据若含 Qualified+ 条目依赖 REGISTRAR 直推降级，行为变化可能破坏既有脚本] → 这是标准对齐的预期 BREAKING 变更，已在 proposal 标注；错误码 1120 消息明确指向批准操作。
- [ADMIN 若非该机构 Registrar 联系人，将无法单独推动 Qualified+ 降级（须拉一名 Registrar 批准）] → 符合 D.4.2/D.4.3"registrar 经委员会审议后执行"的集体决策语义；单人绕过正是本次修复要关闭的缺口。
- [`ensureSeed` 翻转旧规则属静默数据变更] → 既有调和机制本就如此（上次 registration-process-designer 收敛同款）；视图 `requiresApproval` 标记让前端可感知。

## Migration Plan

1. 部署新代码（集合扩充 + 注释/spec）。
2. 各注册机构首次访问流程/执行/批准接口时 `ensureSeed` 自动收敛规则行。
3. 回滚：还原代码后 `ensureSeed` 再次调和回 `false`（种子即真源，无单向迁移）。

## Open Questions

无。
