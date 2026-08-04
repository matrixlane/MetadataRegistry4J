# Tasks

## 1. 会话上下文（后端）

- [ ] 1.1 活动 Registrar 会话模型与存储（账号当前激活 registrarId）
- [ ] 1.2 登录后身份解析：单身份自动激活；多身份返回待选列表；无身份标记为不可注册
- [ ] 1.3 激活/切换/查询当前身份的 REST

## 2. 判定与闸门（后端）

- [ ] 2.1 `isOwnNamespace(ns, activeRegistrar)` 判定函数（以 `Registrar.registrationAuthority` 为准）
- [ ] 2.2 `NamespacePermissionEvaluator.isRegistrar`/`isAnyRegistrarOrAdmin` 判定链迁移为实体关联判定
- [ ] 2.3 标识生成入口接入闸门：外部命名空间拒绝自动生成（与 `namespace-identifier-strategy` D6 对接）
- [ ] 2.4 策略编辑 REST 接入"所属 RA 的激活 Registrar"校验
- [ ] 2.5 注册写操作要求活动 Registrar，registrar 归属写入管理记录

## 3. 前端

- [ ] 3.1 顶栏 Registrar 身份切换器（展示 RA + Registrar 名，多身份可切换）
- [ ] 3.2 注册相关入口未激活身份时的选择引导

## 4. 测试

- [ ] 4.1 激活规则：单/多/无身份账号、SUPER_ADMIN 无身份同样受限
- [ ] 4.2 判定函数：自有/外部/未关联 RA；角色名变更不影响判定
- [ ] 4.3 闸门集成：外部命名空间拒绝生成、非所属 RA 不可编辑策略
- [ ] 4.4 身份切换后注册归属正确性
