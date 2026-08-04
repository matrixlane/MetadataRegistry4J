## Why

当前系统允许调用方在创建管理项时自由传入 `registrationAuthorityId`，甚至留空：条目对注册机构的归属（所有权）由客户端任意指定或缺失，而非由当前登录用户的注册员（Registrar）身份决定。这导致所有权语义形同虚设——任何已认证用户都可以把条目挂到任意注册机构名下，或创建无归属的"自有"条目；注册员与账号的绑定关系（`registration_authority_registrar` 关联）在条目创建/变更链路中完全没有发挥作用。随着注册表级合并（`2026-08-02-registry-merge-strategy`）临近，权属来源不清会成为合并裁决与跨实例身份治理的隐患。

## What Changes

- 引入"活动注册员"解析：服务端依据当前登录账号解析其 Registrar 绑定，得到活动注册机构；当账号同时是多个注册机构的注册员时，提供活动注册机构的选择与切换（默认唯一/首个绑定）。
- **BREAKING** 创建/更新管理项时，注册机构归属由服务端依据活动注册员自动推导，移除请求体中的 `registrationAuthorityId` 字段；客户端不再能指定或清空条目归属。
- 变更权限约束：仅条目所属注册机构的注册员（以其活动注册员身份）可修改该条目；无注册机构归属的外部编目条目维持既有规则；无注册员身份的用户不得在注册机构自有命名空间下创建条目。
- 联邦合并引入的条目 MUST 保留其来源注册机构所有权，本地活动注册员不得将其归属变更为本地注册机构。
- 前端条目表单移除注册机构选择控件，改为只读展示由服务端推导的归属注册机构。

## Capabilities

### New Capabilities

- `active-registrar-ownership`：活动注册员解析、条目所有权自动归属、变更权限约束与联邦条目所有权保持。

### Modified Capabilities

（无。既有 spec 未在需求层面规约 `registrationAuthorityId` 的客户端传入行为；相关合并语义在 `registry-merge-strategy` 启动时再衔接。）

## Impact

- 后端：`ItemAdministrationService`（创建/更新时所有权推导与权限校验）、`ItemAdministrationRequest` DTO（移除 `registrationAuthorityId`）、新增活动注册员解析服务（复用 `ContactRepository.findByAccount` 与 `NamespacePermissionEvaluator` 的既有解析模式）、`MergeExecutor`（禁止改写合并条目归属）。
- API：管理项创建/更新接口请求体移除 `registrationAuthorityId`（**BREAKING**，前端同步调整）；响应继续返回归属注册机构信息。
- 前端：条目创建/编辑表单移除注册机构下拉，改为只读归属展示；多注册机构注册员的顶部栏活动注册机构切换入口（若适用）。
- 数据库：如需持久化"活动注册机构选择"，在账号表新增可空列（UUID/VARCHAR(36)，不绑定 schema，遵守数据库可移植性规则）。
- 测试：`ItemAdministrationServiceTest`、权限集成测试、联邦合并回归测试。
