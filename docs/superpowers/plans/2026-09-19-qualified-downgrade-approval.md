# 实施计划: qualified-downgrade-approval

日期：2026-09-19
设计：`docs/superpowers/specs/2026-09-19-qualified-downgrade-approval-design.md`
OpenSpec：`openspec/changes/qualified-downgrade-approval`（proposal/design/specs/tasks 已齐备并通过 `openspec validate`）

> **修订（2026-09-19 当日）**：默认审核点最终 **10 处**；`CANDIDATE→RECORDED` 治理加严按 4.4 c) 撤销，恢复直推（任务 2.5）。

## 实施顺序（对应 tasks.md）

1. **TDD 红**：先更新/新增测试断言（tasks 2.1–2.4），跑测试确认按预期失败。
2. **实现**（tasks 1.1–1.2）：
   - `RegistrationLifecycleService.java`：
     - `DEFAULT_APPROVAL_TRANSITIONS` 增加 7 条：`QUALIFIED→RETIRED`、`STANDARD→RETIRED`、`PREFERRED_STANDARD→RETIRED`、`QUALIFIED→SUPERSEDED`、`STANDARD→SUPERSEDED`、`PREFERRED_STANDARD→SUPERSEDED`、`SUPERSEDED→RETIRED`。
     - 更新常量 javadoc：依据 11179-6:2023 D.4.2/D.4.3（第 35 页）与 D.2.9；保留按迁移对判定说明（`RECORDED→RETIRED`/`RECORDED→SUPERSEDED` 不在集合内，直推不受影响）。
     - 类级 javadoc 与 `ROLE_PERMISSIONS` 注释对齐新语义。
   - `RegistrationProcessService.java`：仅注释表述更新（"4 处默认审核点"等），无逻辑改动——播种/直推守卫/视图全部经由 `isDefaultApprovalTransition` 自动获得新语义。
3. **TDD 绿**：跑模块测试至全绿（task 3.1）。
4. **收尾**：openspec validate（已预跑通过，最终再跑一次）、GitNexus detect_changes、`.superpowers-memory` 更新。

## 关键回归点

- `ensureSeed` 调和逻辑无改动：旧库 7 条规则将在下次 `ensureSeed` 自动翻转 `requires_approval=true`（displayName 非空定制规则不触碰）。
- `applyTransition` 生效路径跳过角色矩阵（既有设计）：Qualified+ 降级的生效授权完全由 quorum 表达，`ROLE_PERMISSIONS` 中 `→SUPERSEDED` 的 ADMIN-only 行不再出现在生效路径上，但保留给静态底线路径。
- 错误码路径不变：直推需审核迁移 → 1120（归属 RA）；无 RA → 1120 + "未归属注册机构"提示。

## 验证命令

```bash
mvn -pl metadata-registry-web test
openspec validate qualified-downgrade-approval
```
