# Design: 活动 Registrar 硬约束与自有命名空间判定

## 背景

现状（代码事实）：

- `Account` → `Contact`（`contactRepository.findByAccount`，一对多）→ `Organization` 关联链已实现；`Registrar extends Contact`，以 `registrationAuthority`（ManyToOne）挂到注册机构。
- `NamespacePermissionEvaluator.isRegistrar` 按 `Contact.role.roleName == "注册管理员"` + 组织匹配判断，未走 Registrar 实体的 RA 关联——字符串语义脆弱，且与委托签发模型不吻合。
- 无"活动 Registrar"会话概念：权限评估将账号的全部 Contact 身份混合计算，任何时刻无法回答"当前以哪个 Registrar 身份工作"。
- `Namespace.registrationAuthority` 为 `@OneToOne`（0..1），关联注释语义为"RAI 由该命名空间作用域"；用户已决定 RA 与自有命名空间遵循 ISO/IEC 11179-3 原文档的 1:1 关系（两边 0..1），不放宽。

## 决策

### D1：活动 Registrar 会话状态与激活规则

- 账号可拥有多个 Registrar 身份（作为不同 RA 下的 Registrar Contact）。委托签发 = 授权组织外用户成为本 RA 下的 Registrar，其在该 RA 自有命名空间内的行为与本组织 Registrar 无异，无需第三态。
- 硬约束：进行注册工作前必须激活且仅激活一个 Registrar 身份。激活规则：登录后仅一个 Registrar 身份 → 自动激活；多个 → 必须显式选择；无 → 不能执行注册工作。SUPER_ADMIN 不豁免：注册行为必须落在具体 Registrar 记录上，保证 `Scoped_Identifier` 的 registrar 归属可审计。
- 会话存储：账号当前激活的 registrarId（会话级状态，可切换；切换即失效旧上下文并以新身份重建）。

### D2：自有/外部判定函数

`isOwnNamespace(ns, activeRegistrar)` = `ns.registrationAuthority != null && ns.registrationAuthority == activeRegistrar.registrationAuthority`。判定 MUST 以 Registrar 实体的 RA 关联为准，现有角色名匹配判断链随之迁移，避免语义漂移。判定结果为单值布尔，是 `namespace-identifier-strategy` 中生成闸门（其 D6）与设置分支（其 D5）的输入。

### D3：闸门落点

1. **标识自动生成入口**：仅当目标命名空间相对当前激活 Registrar 为自有时放行；外部命名空间拒绝生成（与 `namespace-identifier-strategy` 的外部手动策略一致）。
2. **策略编辑 REST**（标识生成/版本生成策略写操作）：仅允许该命名空间所属 RA 的激活 Registrar。
3. **注册写操作**：必须有活动 Registrar，registrar 归属写入管理记录。

### D4：前端

顶栏 Registrar 身份切换器：展示当前身份（RA 名 + Registrar 名），多身份账号可切换；注册相关入口在未激活时展示引导（跳转身份选择）；切换后刷新会话上下文与相关视图。

### D5：与既有权限评估的关系

`NamespacePermissionEvaluator` 的读写权限模型（组织/账号级授权）保持不变；本变更新增"注册者身份"维度，与读写权限正交：有写权限 + 有活动身份才可执行注册工作。`isRegistrar`/`isAnyRegistrarOrAdmin` 迁移为以 `Registrar.registrationAuthority` 关联判定，调用方语义不变。
