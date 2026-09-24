# Design: Submitter 联系人身份体系

详细设计已在 `docs/superpowers/specs/2026-09-22-submitter-contact-identity-design.md` 中与用户确认，本文档为 OpenSpec 侧的实现决策摘要，不重复需求论证。

## Context

- `Account → Individual → Contact`；`contact_account_binding`（ext 卫星表）已支持一账号多 Contact（`contact_id` 唯一、`account_id` 不唯一）。
- `Registrar extends Contact`（JOINED）；`SubmissionRecord` 实体存在但业务链路无人生成。
- 权限三层：`SecurityConfig` 写操作需 `SUPER_ADMIN/REGISTRAR`；`ItemAdministrationService.resolveOwnershipForCreate` 要求活动 Registrar 并归属 RA；`NamespacePermissionAspect` + `ActiveRegistrarService.assertCanModifyItem` 控制修改/删除。
- 单注册表模式：`RegistrationAuthority` 全库唯一，`Administered_Item.registration_authority_id` 永不为空。
- 状态推进角色矩阵（`RegistrationLifecycleService.ROLE_PERMISSIONS`）限 Registrar/Admin。

## Goals / Non-Goals

**Goals:**

- 让"已登记 Contact 的普通用户"获得 Submitter 写能力：创建/编辑/提交条目。
- 保持既有 Registrar/Admin 行为完全兼容。
- 接通 SubmissionRecord 持久化：Item create/update 同事务生成，`contact`/`organization` 默认预填可改。
- 首登可跳过引导 + "我的"页联系人身份管理 + 默认联系人。

**Non-Goals:**

- 不引入独立 SUBMITTER 角色表或受理工作流。
- 不放开生命周期状态治理权限。
- 不支持多注册表/多 RA 归属切换。

## Decisions

### D1：Submitter 能力表达为 JWT claim，而非新角色表

- `JwtService` 新增 `submitter` claim（账号绑定 Contact 数 ≥1）；`JwtAuthenticationFilter` 映射为 `ROLE_SUBMITTER`；`SecurityConfig` 写操作放行 `SUPER_ADMIN/REGISTRAR/SUBMITTER`。
- 备选：在 `role` 表加 Submitter 角色。否决：Contact 身份是动态派生（删联系人即失权），落表会产生同步负担。

### D2：条目归属推导集中在 `resolveOwnershipForCreate` 一处

优先级：活动 Registrar → 其 RA（现状）；否则 Submitter → 系统唯一 RA。查询唯一 RA 失败沿用既有错误。修改/删除校验（`ActiveRegistrarService.assertCanModifyItem` 与 Aspect）加 Submitter 通道：无活动 Registrar 但为 Submitter 且条目归属本系统 RA 即放行。
- 备选：给 Submitter 虚拟一个"活动 Registrar"。否决：污染 Registrar 语义，影响标识/命名空间自有判定。

### D3：默认联系人落在 `contact_account_binding.is_default`

- 新增列（三方言迁移），应用层事务保证每账号至多一个默认（设默认时清其他）。删默认后自动递补最早绑定的一条；删空后账号自然失去 Submitter 能力。
- 备选：`account.default_contact_id` 外键。否决：跨模块（ext 卫星表归属）且无法表达"Registrar 行也可为默认"。

### D4：SubmissionRecord 在 Item Service 层同事务生成

- 各 Item create/update 时构建 `SubmissionRecord`（contact、organization、registeredItems）；DTO 新增可选 `submission: { contactId, organizationId }`；缺省预填 = 默认联系人 + 其组织；`contactId` 必须属于当前账号绑定的 Contact（服务端强校验，防代填）。
- 新建预填在界面完成、服务端兜底同一逻辑（`ContactService` 提供 `resolveDefaultSubmissionContext(accountId)`）。

### D5：首登引导由 `profilePrompt` 标志驱动

- login/me 响应新增 `hasContact`/`hasRegistrar`/`profilePrompt`（非 SUPER_ADMIN && 无 Registrar && 无 Contact）。前端一次性弹窗，可跳过，localStorage 标记不重复骚扰。Admin 选为 Registrar 时 `profilePrompt=false`，天然满足需求 2。

### D6：状态推进不动

`RegistrationLifecycleService.ROLE_PERMISSIONS` 与运行时角色解析不变；Submitter 不参与状态治理。

## Risks / Trade-offs

- [Submitter 写通道绕过自有/外部命名空间判定] → Submitter 无活动 Registrar，命名空间"自有"判定不变（仍基于激活 Registrar），Submitter 走外部/系统默认命名空间路径，行为与现有非 Registrar 一致的部分保持不变；标识自动生成闸门不受影响。
- [删空联系人后正在进行的会话仍持旧 JWT] → JWT 短有效期 + claim 在网关强校验，下一个写请求即 403，可接受。
- [update 生成新 SubmissionRecord 导致记录膨胀] → 符合"每次提交一版新内容生成一条"的需求语义；导出功能已能消费该表。
- [达梦方言 `is_default` 迁移未实测] → 与既有迁移同等处理，录入遗留跟进；SQLite/SQL Server 实测验证。

## Migration Plan

1. 三方言迁移加列（`DEFAULT 0`，既有绑定行非默认）。
2. 后端部署后旧账号无默认联系人 → 首次提交时服务端预填逻辑回退到"绑定列表第一条"并惰性置默认。
3. 回滚：迁移 down 脚本删列；旧版本代码无 `submitter` claim 时写操作恢复原行为（仅 Registrar/Admin），向后兼容。

## Open Questions

无（需求语义已与用户逐条确认）。
