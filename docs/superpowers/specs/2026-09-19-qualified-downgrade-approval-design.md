# Qualified+ 降级迁移审核前置 — 设计文档

日期：2026-09-19
状态：已确认（依据 ISO/IEC 11179-6:2023 原文证据，用户指令"根据新的证据修改注册流程的流向审核需求"）
OpenSpec 变更：`qualified-downgrade-approval`

> **修订（2026-09-19 当日）**：经 4.4 c)/d) 与 D.2.3 原文逐条复核（详见 session-journal/2026-09-19-qualified-downgrade-approval.md），按用户"尽你所能与标准对齐"裁定：
> ① 新增 D5 决策——撤销 CANDIDATE→RECORDED 治理加严审核点（4.4 c)：submitter/steward 补齐强制属性即可晋升，无需批准；D.2.3 steward 复审/registrar 筛查为建议性实践，以 REGISTRAR 角色门槛近似）；
> ② 默认审核点最终为 **10 处**（3 条 4.4 d) 向上 + 7 条 Qualified+ 降级），非 11 处；
> ③ RECORDED→QUALIFIED 依据补充：D.2.4 Step 3 建议流程为 registrar 复审 + control committee 批准双环节，实现以 Registrar quorum 近似注册机构批准。

## 背景与依据

前一轮核查发现既有实现与标准存在偏差。原文证据（本地 `ISO/IEC 11179 标准原文目录\ISO_IEC 11179-6-2023.pdf`）：

- **D.4.2（第 35 页）**："The status of an administered item proposed for retirement is changed to 'Retired' by the registrar for administered items in the 'Qualified' registration status or higher **after presentation to the control committee**. The submitter may change the registration status of administered items at the 'Recorded' levels to 'Retired' at any time, **without review by the control committee**."
- **D.4.3（第 35 页）**：Superseded 同理——Qualified 及以上须经 control committee；Recorded 级 submitter 无需审查可随时变更。
- **D.2.9（第 32–33 页）**：Superseded → Retired 走 "Provisionally Retired" 行政状态，"If 'Provisionally Retired' administered items are **not approved by the control committee** … they retain the 'Superseded' registration status"——即 Superseded→Retired 同样需要委员会批准。
- **4.4 d)（第 9 页）**："Progression of administered items to registration status of 'Qualified' or higher shall require the sponsorship of a steward and approval of the registration authority."（向上迁移，现有 4 审核点已覆盖）
- **4.3.3.1.8 / 4.3.3.1.9（第 8 页）**：Superseded / Retired 均定义为 "the **registration authority** has determined …" ——降级是注册机构的决定行为。

既有实现的偏差：`DEFAULT_APPROVAL_TRANSITIONS`（`RegistrationLifecycleService.java:382`）只含 4 对向上迁移，`QUALIFIED/STANDARD/PREFERRED_STANDARD → RETIRED/SUPERSEDED` 与 `SUPERSEDED → RETIRED` 均为 `requires_approval = false`，REGISTRAR 可直接执行，无任何审批前置。

## 目标

对齐 D.4.2/D.4.3/D.2.9：Qualified 及以上的降级迁移须经批准后方可生效；Recorded → Retired/Superseded 保持直接执行（标准明确 submitter 无需审查）。

## 方案比较

### 方案 A（推荐）：扩充默认审核点集合

把 7 条 Qualified+ 降级迁移加入 `DEFAULT_APPROVAL_TRANSITIONS`：

- `QUALIFIED→RETIRED`、`STANDARD→RETIRED`、`PREFERRED_STANDARD→RETIRED`
- `QUALIFIED→SUPERSEDED`、`STANDARD→SUPERSEDED`、`PREFERRED_STANDARD→SUPERSEDED`
- `SUPERSEDED→RETIRED`

保留 `RECORDED→RETIRED`、`RECORDED→SUPERSEDED` 为直接执行。

机制上零新增代码路径：`ensureSeed` 以 `isDefaultApprovalTransition` 为真源播种/调和（`RegistrationProcessService.java:137-141,178-179`），旧库中这些规则的 `requires_approval` 会在下次 `ensureSeed` 时自动翻转为 `true`（displayName 非空的定制规则不受影响）；直推路径（`execute`/`transition`）对 `requiresApproval=true` 的规则统一拒绝（1120）；生效走既有 `approve()` quorum 累积 → `applyTransition`。无归属注册机构的条目走静态底线 + M1 守卫：这 7 条落入默认审核集合后拒绝直推，提示先归属注册机构，与既有语义一致。

**语义变化（决策记录）**：`→SUPERSEDED` 从"ADMIN 直推"变为"该机构 Registrar quorum 批准后生效"。原 ADMIN-only 约束（"重大管理决策"）由审批机制取代：quorum 批准 ≈ control committee 审议，registrar 批准人执行 ≈ 标准中 "changed … by the registrar"。`ROLE_PERMISSIONS` 中的 ADMIN-only 行保留（静态底线路径的角色校验仍生效），但归属注册机构的条目生效路径不再经过角色矩阵（`applyTransition` 跳过角色校验，既有设计：授权已由 quorum 表达）。

### 方案 B：仅 Retired 纳入审核，Superseded 保持 ADMIN 直推

把 ADMIN 角色映射为 control committee。问题：D.4.3 与 D.4.2 是平行条款，只审一个造成语义不对称；且 ADMIN ≈ 委员会只是近似，无集体决策记录。不推荐。

### 方案 C：引入 administrative status 层（Provisionally Retired/Superseded）

完整建模 Annex D 的双层状态。属于 WI-009-2 正交维度的大工程，远超本次对齐修复范围。不推荐（可作为后续变更）。

## 影响面

- `RegistrationLifecycleService.DEFAULT_APPROVAL_TRANSITIONS` + javadoc/注释
- `RegistrationProcessService` 注释（"4 处默认审核点"表述）
- 既有测试：`RegistrationLifecycleServiceTest`、`RegistrationProcessServiceTest`、`RegistrationProcessControllerTest`、`RegistrationStatusMigrationTest` 中断言 Qualified+ 降级可直推的用例需改为期望 1120 或走批准路径
- 种子规则自动收敛；前端 `availableTransitions` 的 `canExecute`/`canApprove` 由服务端计算，无需前端改动
- spec：`registration-process` 能力"标准默认审核点"需求修订（4 处 → 11 处审核点）

## 验证

- `RegistrationLifecycleServiceTest` / `RegistrationProcessServiceTest` / `RegistrationProcessControllerTest` / `RegistrationStatusMigrationTest` 更新并通过
- `metadata-registry-web` 模块全量测试通过
- `openspec validate qualified-downgrade-approval` 通过
