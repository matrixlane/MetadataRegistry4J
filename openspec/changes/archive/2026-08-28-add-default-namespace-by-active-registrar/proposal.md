## Why

按 ISO/IEC 11179-3 注册模型，每个注册机构（Registration_Authority）对其自有的命名空间（Namespace）负有管辖责任：命名空间作为 RAI 的载体承载其下注册的受管项。`active-registrar-ownership` 能力已要求用户登录后必须激活一个 Registrar 身份方能进行注册工作，且活动 Registrar 的归属注册机构即本次操作的归属机构。当前新建数据元（或其它 Item）时，命名空间选择器始终为空，业务方每次都必须在列表中手动挑选本注册机构对应的命名空间——一项高频但完全可推导的重复选择：基于活动 Registrar 即可无歧义地确定默认命名空间。缺少默认值既拖慢录入，又容易在多 RA 切换或多命名空间列表中误选到错误归属。本变更在不收紧任何硬约束的前提下，引入"按活动 Registrar 归属注册机构自动预选命名空间"的默认行为。

## What Changes

- **后端默认推导**：`ItemAdministrationService.ensureAdministeredItem` 在 `ItemRegistrationRequest.namespaceId()` 为空、调用方具备活动 Registrar 身份且活动 Registrar 所属 Registration_Authority 存在自有命名空间时，按 `Namespace.registrationAuthority == activeRegistrar.registrationAuthority` 取唯一一条命名空间作为请求命名空间；外部命名空间（`namespaceKind == EXTERNAL`）、system-default 命名空间与未与任何 RA 关联的命名空间不参与默认推导。调用方未传 `namespaceId` 且无活动 Registrar 身份时，沿用当前 `enforceRaNamespacePrecondition()` 行为（继续由既有前置条件拒绝非法创建）。
- **活动身份切换响应默认**：当用户在系统中切换活动 Registrar 时，后续新建 Item 的默认命名空间随之切换到新 RA 的自有命名空间；默认推导完全基于会话级活动 Registrar，禁止客户端传任何归属推导相关字段。
- **前端预选**：`ItemRegistrationSection.vue` 在加载完命名空间列表与活动 Registrar 后（`loadOptions` 中两路完成后），若处于创建态（`!props.isEditing`）且 `model.value.namespaceId` 仍为空，则将活动 Registrar 所属注册机构对应的命名空间自动填入选择器；外部/system-default 命名空间不参与默认。用户可在保存前自由切换其它命名空间，默认值仅作为初始选择，不锁定。
- **默认值的边界**：默认值只是 UI 与服务端的初始推导，不改变任何既有校验：`identifierInputMode` / `versionInputMode` / 命名空间前置门槛等行为完全沿用。

## Capabilities

### New Capabilities

- `default-namespace-by-active-registrar`：在新建 Item 时，按活动 Registrar 所属 Registration_Authority 的自有命名空间作为默认命名空间，覆盖后端 `ensureAdministeredItem` 默认推导与前端 `ItemRegistrationSection` 预选。

### Modified Capabilities

无（既有 `identification`、`active-registrar-ownership` 等能力的硬约束不变；本能力在新规范内独立表达）。

## Impact

- **后端**：
  - `ItemAdministrationService.ensureAdministeredItem`：在解析 `req.namespaceId()` 为 null 后，先尝试按活动 Registrar 推导默认命名空间，再走既有 `enforceRaNamespacePrecondition` 校验。
  - 复用 `ActiveRegistrarContext`（来自 `active-registrar-ownership`）解析当前激活的 Registrar；新增 `NamespaceRepository.findByRegistrationAuthority_Id` 查询自有命名空间。
  - 行为变更不影响 `explicitAuthority` 联邦路径：联邦/合并场景仍以 `explicitAuthority` 为准。
- **前端**：
  - `ItemRegistrationSection.vue`：在 `loadOptions()` 内两路解析（命名空间 + activeRegistrar）完成后触发一次默认预选；监听身份切换（参考 `activeRegistrarApi.current()` 重新执行或顶栏切换事件）后，刷新默认预选。
  - 仅在创建态生效，编辑态不可更改命名空间，行为保持现状。
- **数据模型**：无新增实体、无 schema 变更。`Namespace.registrationAuthority`（`@OneToOne`，0..1）的 1:1 不变量已在 `active-registrar-ownership` 中确立，本变更仅在其上做"取值"动作。
- **测试**：补既有 `ItemAdministrationService` 单测覆盖默认推导三类路径（命中/无活动身份/无自有命名空间），以及前端 dialog 的预选回归。
- **依赖**：完全内置于现有模块，不引入新依赖。
