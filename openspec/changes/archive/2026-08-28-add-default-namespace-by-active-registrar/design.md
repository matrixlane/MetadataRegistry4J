# Design: 新建 Item 时按活动 Registrar 默认命名空间

## Context

现状（代码事实）：

- `ItemAdministrationService.ensureAdministeredItem(Item, ItemRegistrationRequest)` 接收的 `req.namespaceId()` 为 null 时不做默认推导，直接将 `ns` 置为 null 后走 `enforceRaNamespacePrecondition`。外部命名空间（`namespaceKind == EXTERNAL`）和 system-default（`urn:metadata-registry:system-default`）以外的、目标为无 RA 关联命名空间的场景，会被既有前置条件在校验环节拒绝。
- `ItemRegistrationSection.vue` 的 `el-select v-model="model.namespaceId"` 初始为空；`loadOptions()` 并行拉取命名空间列表与 `activeRegistrarApi.current()`，但没有把后者结果用于预选命名空间，只在 `ownershipLabel` 中展示当前 RA 名字。
- `active-registrar-ownership` 已确立硬约束：账号登录后必须激活且仅激活一个 Registrar 身份；多身份账号可切换；活动 Registrar 身份由服务端解析，禁止客户端传入归属字段。`Registrar.registrationAuthority` 是判定"自有命名空间"的唯一权威字段。
- `Namespace.registrationAuthority`（`@OneToOne`，0..1）维持 RA 与自有命名空间的 1:1 关系；每个 RA 至多一条 `registrationAuthority != null AND namespaceKind != EXTERNAL` 的命名空间。

## Goals / Non-Goals

**Goals:**

- 在新建 Item（创建态）的入口（前端 dialog 与后端 create API）按活动 Registrar 所属 RA 自动预选/推导其自有命名空间。
- 默认值可视化但可由用户改写：仅作为初始默认值，不锁定、不修改既有校验链。
- 活动 Registrar 切换后，新打开的创建对话框默认随之切换；后端推导按当前请求上下文的活动 Registrar。
- 完整覆盖边界：外部命名空间、system-default、未关联 RA 的命名空间、MULTI/无身份账号——均不进入默认值。

**Non-Goals:**

- 不调整命名空间前置门槛（`enforceRaNamespacePrecondition`）的拒绝语义。
- 不放宽 RA 与自有命名空间的 1:1 不变量，亦不引入新的实体/字段。
- 不修改编辑态命名空间（编辑态命名空间按既有 `selectEditableIdentifier` 维持）。
- 不要求联邦/合并路径（`explicitAuthority`）使用活动 Registrar 默认推导——联邦/合并场景本就以外部权威字段为准。

## Decisions

### D1：默认推导公式

`defaultNamespaceFor(activeRegistrar)` = 唯一满足 `n.registrationAuthority != null AND n.registrationAuthority == activeRegistrar.registrationAuthority AND n.namespaceKind != EXTERNAL AND n.schemeReference != SYSTEM_DEFAULT_SCHEME` 的命名空间。无活动 Registrar 或无对应自有命名空间时返回 null。

### D2：后端接入点

`ItemAdministrationService.ensureAdministeredItem(Item, ItemRegistrationRequest, RegistrationAuthority explicitAuthority)`：

1. 维持现有 `req.namespaceId()` 解析分支作为"调用方显式指定"路径。
2. 在显式未指定时（`req.namespaceId() == null`），调用新内部方法 `resolveDefaultNamespace()`：
   - 若 `explicitAuthority != null`（联邦/合并路径）→ 不做默认推导，沿用显式权威。
   - 否则读取当前活动 Registrar（来自 `ActiveRegistrarContext`，与 `active-registrar-ownership` 共用）；若活动 Registrar 不存在或无自有命名空间 → 返回 null（交给既有的 `enforceRaNamespacePrecondition` 拒绝）。
3. 将推导结果赋给 `ns`，后续命名空间级校验（输入模式、版本策略、命名约定等）完全沿用既有路径。

### D3：前端接入点

`ItemRegistrationSection.vue#loadOptions()`：

1. 维持现有命名空间与活动 Registrar 的并行加载。
2. 在两路 `await` 全部 settled 后调用新增的 `applyDefaultNamespace()`：
   - 仅在 `!props.isEditing`（创建态）执行。
   - 仅当 `model.value.namespaceId == null` 才覆盖（保留用户的显式选择）。
   - `applyDefaultNamespace()` 取 `activeRaName` 对应的命名空间（在命名空间列表中匹配 `registrationAuthorityId == activeRegistrar.registrationAuthorityId`）注入 `model.value.namespaceId`。
3. 监听活动 Registrar 切换：复用项目内统一的 `activeRegistrar` 切换事件/查询刷新（与顶栏切换器共享），切换后清理 `model.value.namespaceId` 并再次执行 `applyDefaultNamespace()`，让默认重新收敛。

### D4：推导边界与外部/system-default 排除

默认推导 MUST NOT 命中外部命名空间（`namespaceKind == EXTERNAL`）与 system-default（`schemeReference == 'urn:metadata-registry:system-default'`）。这两类命名空间是"导入外部数据标准/系统内部标识"的容器，由既有的 `enforceRaNamespacePrecondition` 校验其创建前置条件，与"自有数据标准的默认承载 RA"在语义上不一致。

### D5：默认值的可写性

默认值只是初始选择，不会：

- 写入"上一次选择的命名空间"持久化偏好（避免在多 RA 切换时引入跨身份污染）。
- 改变后端对 `req.namespaceId()` 的解析顺序：调用方显式传入永远优先。
- 增加前端表单字段或隐藏服务端校验。

### D6：与既有闸门的关系

- `enforceRaNamespacePrecondition`：当默认推导返回 null（无活动 Registrar 或无自有命名空间）时该校验触发拒绝行为，提示用户先创建基于注册机构的命名空间。本变更不修改其拒绝条件。
- `identifierInputMode` / `versionInputMode`：默认命名空间落入这些模式后，沿用既有输入框禁用/启用的 computed 行为（`identifierDisabled` / `versionDisabled`）。
- 命名空间前置门槛查询 `readinessApi.raNamespaceExists()`：保留 alert 引导；默认值不可绕开该引导。

## Risks / Trade-offs

- [R1 后端默认推导与服务端 RA 推导不一致] → 默认推导只用于"获取默认命名空间"，不参与"条目归属"判定（后者由 `resolveOwnershipForCreate` 基于 `ns.registrationAuthority` + 活动 Registrar 派生）。两端通过 `Namespace.registrationAuthority` 单字段对齐。
- [R2 前端预选与后端推导时序错位] → 前端仅在 `loadOptions()` 全部分支 settled 后执行预选；用户在该时点之前已经手动选过则保留选择。命名空间列表为空（无自有命名空间）时不报错、不阻塞，只是不预选。
- [R3 多 RA 用户从 A 切到 B 后仍残留 A 的默认] → 切换事件触发 `applyDefaultNamespace()` 重新收敛，覆盖前清空 `model.value.namespaceId`。命名空间列表若未刷新，按已加载列表匹配即可。
- [R4 与"系统/联邦合并"路径并存] → `explicitAuthority != null` 时不默认推导；联邦/合并由外部权威字段决定归属。已有 `selectEditableIdentifier` / `enforceRaNamespacePrecondition` 等不变。
- [R5 与既有 `NamespacePermissionEvaluator` 既有行为脱节] → 本变更不动既有读写权限评估；默认值不引入任何额外授权，仅在用户已具备活动 Registrar 身份时执行。
- [R6 默认推导跨方言] → 不涉及 SQL/迁移；后端仅新增 `findByRegistrationAuthority_Id` 方法，由 JPA 自动派生方言中立查询，符合既有 `db-portability` 规则。

## Migration Plan

- **部署顺序**：能力与 `active-registrar-ownership` 已上线项目同步发布即可；二者均依赖"活动 Registrar 上下文"已存在的事实。
- **回退策略**：若默认推导行为导致业务误用单一命名空间（极端场景），可临时通过配置级开关 `metadata-registry.feature.default-namespace-by-active-registrar=false`（默认 true）关闭服务端默认推导。前端预选在关闭时一并回退为"不预选"。本次首版不引入配置开关，仅在代码评审/试运行阶段观察；如确需开关，作为后续单独变更处理。
- **向后兼容**：调用方显式传入 `namespaceId` 的行为完全不变；未传入但当前没有活动 Registrar 的账号维持既有前置门槛拒绝行为。

## Open Questions

- 多身份账号切换后，前端是否需要在 `ItemFormDialog` 打开瞬间就执行预选，还是仅在 `loadOptions()` 完成时执行一次（然后保留用户改动直到对话框关闭）？当前决策为后者（仅在 loadOptions 中执行）；切换响应后是否在新对话框打开时再触发一次，将在实现时按 dialog 生命周期验证后补一条注释说明（不进入 spec 要求）。
- 若账号存在活动 Registrar 但该 Registrar 的 RA 尚未创建自有命名空间，前端是否需要主动跳转到"创建命名空间"页？当前决策为保留 alert 引导，不在默认推导职责范围内跳转（避免侵入式打断录入流程）。

- 多身份账号切换后，前端是否需要在 `ItemFormDialog` 打开瞬间就执行预选，还是仅在 `loadOptions()` 完成时执行一次（然后保留用户改动直到对话框关闭）？**已确认：一切相关上下文都随之切换**。即活动 Registrar 切换后不仅清空 `model.value.namespaceId` 重新预选，所有依活动身份派生的 UI 状态（命名空间选择、命名空间前置门槛 alert、模板变量加载等）都必须重解析。
- 若账号存在活动 Registrar 但该 Registrar 的 RA 尚未创建自有命名空间，前端是否需要主动跳转到"创建命名空间"页？**已确认：不跳转**。命名空间极少需要创建，注册人员应事先创建好；前端仅维持既有 alert 引导与 `readinessApi.raNamespaceExists()` 的提示。
- 是否引入 `metadata-registry.feature.default-namespace-by-active-registrar` 配置开关？**已确认：当前版本不引入**。能力保持默认开启，技术上保留未来接入开关的可扩展点（`resolveDefaultNamespace` 抽为可注入函数/策略，单元测试可独立验证）。
