## Why

当前系统允许调用方在创建管理项时自由传入 `registrationAuthorityId`，甚至留空：条目对注册机构的归属（所有权）由客户端任意指定或缺失，而非由当前登录用户的 Registrar 身份决定。这导致所有权语义形同虚设——任何已认证用户都可以把条目挂到任意注册机构名下，或创建无归属的"自有"条目；注册员与账号的绑定关系（`registration_authority_registrar` 关联）在条目创建/变更链路中完全没有发挥作用。随着注册表级合并（`2026-08-02-registry-merge-strategy`）临近，权属来源不清会成为合并裁决与跨实例身份治理的隐患。

## What Changes

- 引入"活动 Registrar 身份"：Registrar 才是注册工作的执行身份。用户可同时持有多个 Registrar 身份（云部署多租户场景下，一个租户可授权另一租户的用户成为本租户注册机构的 Registrar——CLOUD 留待未来实现，机制先行兼容）；登录后同时只能激活一个 Registrar 身份，所有注册工作均以该活动身份进行。
- **BREAKING** 注册（创建/更新）Administered_Item 时，归属由服务端依据活动 Registrar 身份自动推导，移除请求体中的 `registrationAuthorityId` 字段；客户端不再能指定或清空条目归属。所注册的 Administered_Item 一定关联活动 Registrar 所在的 Registration_Authority。
- 注册权限：非 Registrar 没有权限注册 Administered_Item；Registrar 有权限注册。个人版 Admin 自动成为所创建注册机构的 Registrar（既有行为）；团队版 Admin 是否成为 Registrar 是可选的（既有行为），未自指派时不具备注册权限，须先被指派为 Registrar。外部命名空间下的条目（导入或按外部标准手工编目）同样归属操作 Registrar 所在的注册机构：外部命名空间本身不关联任何注册机构，其 Naming_Authority 记录该外部标准的真实来源机构。
- 变更权限约束：修改/删除已归属注册机构的条目时，当前用户 MUST 以该机构的 Registrar 身份为活动身份；无归属的外部编目条目维持既有规则。
- 本地创建/更新/删除流程 MUST NOT 改写既有条目的注册机构归属；归属变更仅能通过注册表合并发生——RAI 一致时迁移条目重新关联到目的方注册机构，具体语义由 `2026-08-02-registry-merge-strategy` 规约。
- 前端条目表单移除注册机构选择控件，改为只读展示由服务端推导的归属注册机构；持有多个 Registrar 身份的用户在登录后选择并在使用中切换活动身份。

## Capabilities

### New Capabilities

- `active-registrar-ownership`：活动 Registrar 身份解析与切换、注册权限与条目所有权自动归属、变更权限约束与联邦条目所有权保持。

### Modified Capabilities

（无。既有 spec 未在需求层面规约 `registrationAuthorityId` 的客户端传入行为；个人版/团队版 Admin 的 Registrar 指派语义已在 `deployment-edition` 与 `initialization-wizard` 中规约，本变更仅引用不修改；相关合并语义在 `registry-merge-strategy` 启动时再衔接。）

## Impact

- 后端：`ItemAdministrationService`（注册路径的权限校验与所有权推导）、`ItemAdministrationRequest` DTO（移除 `registrationAuthorityId`）、新增活动 Registrar 身份解析服务（复用 `ContactRepository.findByAccount` 与 `NamespacePermissionEvaluator` 的既有解析模式）、本地各写入路径的归属冻结（合并侧重关联由 `2026-08-02-registry-merge-strategy` 落地）。
- API：管理项创建/更新接口请求体移除 `registrationAuthorityId`（**BREAKING**，前端同步调整）；新增活动 Registrar 身份查询/切换接口；响应继续返回归属注册机构信息。
- 前端：条目创建/编辑表单移除注册机构下拉，改为只读归属展示；多 Registrar 身份用户的活动身份选择与切换入口。
- 数据库：账号表新增可空列持久化活动 Registrar 身份选择（UUID/VARCHAR(36)，不绑定 schema，遵守数据库可移植性规则）。
- 测试：`ItemAdministrationServiceTest`、权限集成测试、联邦合并回归测试。
