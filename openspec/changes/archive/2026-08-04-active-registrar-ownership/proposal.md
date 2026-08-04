## Why

受委托签发/注册的本质是：授权一名组织外用户成为本注册机构下的一名 Registrar——无需引入第三态。一名用户事实上可以作为多个注册机构的 Registrar 开展注册工作，但硬约束是：同一时间必须激活一个且仅一个 Registrar 身份。当前系统没有"活动 Registrar"的会话概念，权限评估将账号下所有 Contact 身份混合计算，且 `isRegistrar` 判断链依赖角色名字符串匹配而非 Registrar 实体的 RA 关联。

命名空间"自有/外部"的判定是 `namespace-identifier-strategy` 变更中设置分支与生成闸门的输入，它必须基于当前激活的 Registrar：命名空间关联的注册机构 == 活动 Registrar 所属注册机构 → 自有，否则外部。缺少这一判定，标识生成策略可能作用到错误的命名空间（例如全局默认策略替外部命名空间生成标识）。

## What Changes

- **活动 Registrar 会话状态**：进行注册工作前必须激活且仅激活一个 Registrar 身份；账号仅有一个 Registrar 身份时自动激活；拥有多个时必须显式选择其一；不具有任何 Registrar 身份的账号 MUST NOT 执行注册工作（SUPER_ADMIN 不豁免——注册行为必须落在具体 Registrar 记录上以保证归属可审计）。
- **前端身份切换器**：顶栏展示并切换活动 Registrar（注册机构 + Registrar 名称）；注册相关入口未激活时引导选择；切换后重建会话上下文。
- **自有/外部判定函数**：以 Registrar 实体的 `registrationAuthority` 关联为准；捋直现有按 Contact 角色名"注册管理员" + 组织匹配的判断链。
- **生成与策略编辑闸门**：仅当目标命名空间相对当前激活 Registrar 为自有时，允许标识自动生成；外部命名空间仅允许手动录入/导入。标识/版本策略的编辑仅允许该命名空间所属注册机构的激活 Registrar。
- **RA 与自有命名空间维持 1:1**（两边 0..1，遵循 ISO/IEC 11179-3 原文档），不放宽。

## Capabilities

### New Capabilities

- `active-registrar-ownership`：活动 Registrar 会话状态与硬约束、身份切换、自有/外部判定、标识生成与策略编辑闸门。

### Modified Capabilities

无。对标识生成入口的闸门以本能力的规范性要求表达，避免与并行变更 `namespace-identifier-strategy` 同时修改 `identifier-auto-generation` 能力；两变更在归档阶段按语义合并。

## Impact

- **后端**：新增活动 Registrar 会话/请求上下文（账号当前激活 registrarId 的存储与解析）；激活/切换/查询 REST；注册写操作与标识生成入口接入判定；`NamespacePermissionEvaluator.isRegistrar` 判定链迁移为以 `Registrar.registrationAuthority` 为准。
- **前端**：顶栏 Registrar 身份切换器；登录后身份解析（单身份自动激活、多身份进入选择流程）；注册入口未激活引导。
- **数据模型**：无新实体；Account→Contact→Registrar→RegistrationAuthority 链已存在，仅新增会话级激活状态存储。
- **关联变更**：`namespace-identifier-strategy` 的命名空间性质设置分支与外部命名空间生成禁令，以本变更的判定结果为输入。
