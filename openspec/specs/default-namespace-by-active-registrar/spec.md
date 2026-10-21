# default-namespace-by-active-registrar Specification

## Purpose
TBD - created by archiving change add-default-namespace-by-active-registrar. Update Purpose after archive.
## Requirements
### Requirement: 新建 Item 时按活动 Registrar 归属注册机构默认命名空间

新建任何受管项（Administered_Item，包括数据元、对象类、特性、数据元素概念、值含义、计量单位、值域、概念域、数据类型等 Item 子类，下称"新建 Item"）时，若调用方未在 `ItemRegistrationRequest` 中显式携带 `namespaceId`，后端 MUST 按当前活动 Registrar 所属 Registration_Authority 推导默认命名空间：取唯一满足 `n.registrationAuthority != null` 且 `n.registrationAuthority == activeRegistrar.registrationAuthority` 且 `n.namespaceKind != EXTERNAL` 的命名空间。该默认值仅在调用方完全未传 `namespaceId` 时使用；调用方显式传入的值始终优先。前端创建对话框在命名空间选择器上 MUST 以同一推导结果预选；用户可在保存前自由调整，默认值不锁定。联邦/合并路径以 `explicitAuthority` 为权威，本要求 MUST NOT 在该路径上覆写。

#### Scenario: 活动 Registrar 命中自有命名空间

- **WHEN** 当前账号的活动 Registrar 所属 Registration_Authority 存在自有命名空间 N，且 `ItemRegistrationRequest` 未携带 `namespaceId`
- **THEN** 后端 MUST 取命名空间 N 作为请求命名空间，命名空间前置门槛与 `identifierInputMode` / `versionInputMode` 等既有校验沿用既有路径生效；前端创建对话框 MUST 将命名空间选择器预选为 N

#### Scenario: 调用方显式传入命名空间时不被覆盖

- **WHEN** 调用方在 `ItemRegistrationRequest` 中显式携带了 `namespaceId`
- **THEN** 后端 MUST NOT 触发默认推导，直接采用调用方传入的 `namespaceId`；前端 MUST NOT 因默认预选而覆盖调用方/用户的显式选择

#### Scenario: 无活动 Registrar 时不默认

- **WHEN** 当前账号没有任何 Registrar 身份（或活动 Registrar 失效）
- **AND** `ItemRegistrationRequest` 未携带 `namespaceId`
- **THEN** 后端 MUST 将命名空间置为 null 并沿用 `enforceRaNamespacePrecondition` 既定拒绝行为；前端 MUST NOT 进行预选

#### Scenario: 活动 Registrar 所属 RA 未建自有命名空间时不默认

- **WHEN** 当前账号的活动 Registrar 所属 RA 尚未创建自有命名空间
- **AND** `ItemRegistrationRequest` 未携带 `namespaceId`
- **THEN** 后端 MUST 将命名空间置为 null 并沿用既有前置门槛行为；前端 MUST 沿用既有的"先创建基于注册机构的命名空间"补建引导

#### Scenario: 外部命名空间与 system-default 不参与默认

- **WHEN** 系统中存在与活动 Registrar RA 无关的 `EXTERNAL` 命名空间
- **THEN** 这些命名空间 MUST NOT 被推导为默认；外部命名空间创建路径继续由既有前置门槛管控

#### Scenario: 活动 Registrar 切换后默认切换

- **WHEN** 多身份账号的活动 Registrar 从 RA-A 切换至 RA-B
- **THEN** 后续新建 Item 时，后端 MUST 取 RA-B 的自有命名空间作为默认（若存在）；前端创建对话框在新一次打开或活动身份重解析后 MUST 将默认预选刷新到 RA-B 的自有命名空间

#### Scenario: 多身份账号唯一自有命名空间被严格唯一选定

- **WHEN** 活动 Registrar 所属 RA 同时存在多条满足 `n.registrationAuthority == RA` 的命名空间
- **THEN** 后端 MUST 视为数据/一致性错误并按既有一对一不变量拒绝默认推导（MUST NOT 任选其一）；运维侧需修复命名空间归属后方可重试

#### Scenario: 系统以单一活动 Registrar 持久化偏好时不得将默认命名空间持久化

- **WHEN** 用户在创建对话框中接受了默认命名空间或切换到其它命名空间
- **THEN** 系统 MUST NOT 将该命名空间持久化为"上一次选择"之类的偏好，避免多 RA 账号的身份切换被旧归属污染

### Requirement: 默认推导完全基于服务端活动 Registrar 上下文

默认命名空间的取值 MUST 在服务端仅依据 `ActiveRegistrarContext`（来自 `active-registrar-ownership` 能力）解析的活动 Registrar 计算；前端 MUST NOT 向后端传递任何可被解释为"默认 RA / 默认命名空间"的入参字段。请求体继续遵循 `active-registrar-ownership` 已确立的约束：不得接受 `registrationAuthorityId` 或等效归属字段。

#### Scenario: 前端不携带归属字段

- **WHEN** 前端为 `ItemRegistrationRequest` 构造请求体
- **THEN** 该请求体 MUST NOT 包含 `registrationAuthorityId`、`owningRegistrationAuthorityId`、`defaultNamespaceId` 之类的字段；既有禁止字段保持禁止

#### Scenario: 服务端推导来源于当前会话活动 Registrar

- **WHEN** 后端服务收到未携带 `namespaceId` 的 `ItemRegistrationRequest`
- **THEN** 默认推导 MUST 读取当前会话级 `ActiveRegistrarContext`，不得使用客户端传入的身份/RA 标识

