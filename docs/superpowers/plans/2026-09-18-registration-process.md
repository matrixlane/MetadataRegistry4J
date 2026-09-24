# 注册流程实现计划

日期：2026-09-18
来源：`openspec/changes/registration-process-designer/`（tasks.md 已确认）+ `docs/superpowers/specs/2026-09-18-registration-process-design.md`

## 实施策略

- 主工作区直接实施（不开 worktree）：本功能依赖上一轮未提交的菜单重构，隔离会丢失基础。
- TDD：先写/改测试，再实现；每组任务完成即跑对应验证命令。
- 顺序：后端（任务 1-2）→ 前端（任务 3-5）→ 端到端与文档（任务 6）。

## API 契约（前后端对齐基准）

```
GET /api/v1/registration-process
→ ApiResponse<{ authorityId, transitions: [{ from, to, displayName, requiresApproval,
   approverMode: ALL_REGISTRARS|SELECTED_REGISTRARS, quorumType: ANY_ONE|AT_LEAST,
   quorumCount, approverRegistrarIds: [], enabled }] }>

GET /api/v1/administered-items/{id}/transitions
→ ApiResponse<[{ to, displayName, requiresApproval, approvedCount, quorum,
   canExecute, canApprove, approvedByMe }]>

POST /api/v1/administered-items/{id}/transitions/execute  { to, note }  → ApiResponse<AdministeredItemResponse>
POST /api/v1/administered-items/{id}/transitions/approve  { to }        → ApiResponse<{ executed: boolean, transitions: <上条目的最新视图> }>
```

## 关键实现注意点

1. 规则实体放 `commonfacilities/registration` 包（与 RegistrationState 同包）；Repository/Service/Controller 放 web 模块，沿用既有分层。
2. 规则查询直接走 Repository（每 RA 仅 17 条规则，不做缓存；design.md 中"缓存"按此简化执行）。
3. `RegistrationLifecycleService.transition` 保持为底层状态写入入口；审批生效路径在同事务内调用它。
4. `ItemRegistrationStateSection` 只在**编辑模式**只读；新建模式仍允许选择初始状态（含 APPLICATION/HISTORICAL 标定）。
5. 菜单变更只是注册模块内两项指向调整，其余分组不动。
6. 每期验证基线：后端 `mvn test`、前端 `npx vitest run`、`npm run build`、`npx eslint`（0 error）、相关 e2e。

## 任务拆分

严格按 `openspec/changes/registration-process-designer/tasks.md` 的 14 项执行并逐项勾销。
