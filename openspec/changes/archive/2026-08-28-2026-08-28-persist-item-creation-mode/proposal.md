# 持久化 Item 标识符与版本的 AUTO/MANUAL 创建选择

> OpenSpec 提案 — 2026-08-28（最终版）

## Why

所有 Item 子类（DataElement、DataElementConcept、ObjectClass、Property、ValueMeaning、MeasureClass、UnitOfMeasure、DataType、DataTypeScheme、ConceptualDomain、ValueDomain、Context、ConstraintSet、NamingConvention、RegistrySpecification 等）的注册区段 `ItemRegistrationSection.vue` 在**创建**时已允许用户在内嵌切换控件上为标识符和版本独立选择"自动生成"或"手动输入"。然而这个选择**没有持久化到数据库**：编辑既有 Item 时，前端与后端采用固定的语义（标识符始终禁用、版本始终可编辑并按策略自动递增），与用户创建时的真实选择脱节。

业务诉求很直接：

- 用户在创建时若选择"标识符自动生成"，期望后续编辑也只能沿用自动生成（按命名空间策略按需重生成），**不允许手动改写**；
- 用户在创建时若选择"标识符手动输入"，期望后续编辑可手动修订标识符（沿用现行单 SI 可改语义）；
- 同理对版本字段：自动模式下编辑时禁止手动指定、只能由系统按命名空间版本策略递增；手动模式下编辑时允许用户显式指定版本；
- 这些选择还应当作为 Item 的**持久化元数据**留下来，以便将来通过 API 检索时直接看到每个 Item 创建时是按自动还是手动方式生成的。

具体生成策略类型（`UUID` / `URN` / `SEQUENCE` / `TEMPLATE` / `SEMVER` / `SEQUENTIAL` / `DATE`）属于命名空间级配置（`IdentifierStrategyConfig.strategyType` / `Namespace.versioningPolicy`），由命名空间统一管理，**不需要在每条 SI 上重复保存**。本变更只记录创建时用户层面的 AUTO/MANUAL 二元选择。

## What Changes

- **数据模型**：`ScopedIdentifier` 实体新增两个可空枚举字段 `identifierCreationMode` 与 `versionCreationMode`（取值 `AUTO` / `MANUAL`，枚举类 `IdentifierCreationMode`，可空）。数据库表 `scoped_identifier` 新增对应列（三种方言均需迁移脚本）。
- **API 请求 DTO**：`ItemRegistrationRequest` 增加两个可选枚举字段 `identifierMode`、`versionMode`（`IdentifierCreationMode`），由前端在创建时显式携带；后端据此将模式持久化到 SI。
- **API 响应 DTO**：`ScopedIdentifierResponse` 与 `ItemMetaResponse` 增加 `identifierCreationMode` 与 `versionCreationMode`（均可空），便于：
  - 前端在编辑态基于历史选择决定输入控件状态；
  - 任意时刻通过 API 检索 Item 的创建选择（自动还是手动）。
- **后端创建路径**：`ItemAdministrationService.ensureAdministeredItem` 在创建第一个 ScopedIdentifier 时根据 `req.identifierMode()` 与 `req.versionMode()` 写入对应枚举字段；当请求未携带 mode 时，按"identifier / version 是否有显式值"反推（非空 → MANUAL；空 → AUTO）。
- **后端编辑路径**：
  - AUTO：标识符不接受任何请求体改动，后端 MUST 拒绝；版本不接受手动值，留空则按命名空间版本策略递增。
  - MANUAL：标识符允许在单 SI 条件下修改（沿用现有 `selectEditableIdentifier` 与 `scopedIdentifier.size() == 1` 约束），版本接受请求体值并按命名空间版本策略校验；空时按策略递增。
  - NULL（兼容既存记录）：与 MANUAL 等价处理，保持当前 UI / 后端语义不变。
- **后端联邦合并与外部命名空间路径**：合并时新落地的 SI 继承来源方的 mode 字段；外部命名空间下的 SI 默认写 NULL（按 MANUAL 路径处理）。
- **前端编辑态**：`ItemRegistrationSection.vue` 根据 `ItemMetaResponse` 回传的 `identifierCreationMode` / `versionCreationMode` 决定输入框与内嵌切换控件的状态：
  - AUTO：输入框 MUST 禁用、占位符"自动生成不可编辑"，内嵌切换控件 MUST 不显示；
  - MANUAL：输入框 MUST 启用，内嵌切换控件可显示（沿用既有 `AUTO_WITH_OVERRIDE` 行为）；
  - NULL（兼容）：等价 MANUAL。
- **前端新建态**：不变（用户在 inline 控件上选择 AUTO / MANUAL，该选择经 `ItemRegistrationRequest.identifierMode` / `versionMode` 传至后端）。
- **前端宿主组件**：`ItemFormDialog` 等仅透传 `ItemMetaResponse` 中两个 mode 字段给 `ItemRegistrationSection`，不引入其他修改。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities

- `identification`：扩展"编辑态标识符/版本语义统一"要求，明确编辑路径的可编辑性由创建时持久化的 `identifierCreationMode` / `versionCreationMode` 决定（AUTO 锁定 / MANUAL 开放 / NULL 等价 MANUAL）。同时扩展"所有 Item 子类界面提供统一的标识符/版本编辑区段"，要求编辑态依据这两个 mode 字段切换 UI；`ItemMetaResponse` MUST 暴露这两个字段供编辑态透传与将来 API 检索。
- `identifier-auto-generation`：在"命名空间内唯一标识符自动生成"与"前端标识符生成模式切换与验证控制"中，补充"创建时用户选择的 AUTO / MANUAL MUST 持久化到 `ScopedIdentifier.identifierCreationMode` / `versionCreationMode`，编辑态据此决定行为，响应体 MUST 暴露这两个字段"的硬性要求。

## Impact

- **后端领域模型**：`metadata-registry-common-facilities` 的 `ScopedIdentifier.java` 新增两个枚举字段；新增 `IdentifierCreationMode` 枚举类（AUTO / MANUAL）。
- **后端服务层**：`metadata-registry-web` 的 `ItemAdministrationService.ensureAdministeredItem` 在创建与编辑分支上读取并写入 mode 字段；`ScopedIdentifierService`、`ScopedIdentifierController` 与对应 Response DTO 暴露 mode 字段。
- **后端 DTO**：`ItemRegistrationRequest` 增加可选枚举；`ScopedIdentifierResponse`、`ItemMetaResponse` 暴露 mode 字段。
- **数据库迁移**：三种方言各一份脚本，新增两列。
- **前端共享组件**：`metadata-registry-ui/src/components/ItemRegistrationSection.vue` 接收两个 mode 字段 props，在 `isEditing` 分支下决定输入框禁用与内嵌控件显示。
- **前端测试**：`ItemRegistrationSection.spec.ts` 新增编辑态 AUTO / MANUAL / NULL 三类场景。
- **后端测试**：`ItemAdministrationServiceTest`、`ScopedIdentifierServiceTest` 新增覆盖创建时持久化 mode、编辑态按 mode 分支等场景。
- **数据库 / 迁移**：必须可移植（SQL Server / SQLite / 达梦 三方言等价迁移脚本）。
- **依据**：ISO/IEC 11179-3:2023 第 7 章（Scoped Identifier）对 identified item 的标识符与版本的命名空间绑定未受影响；新增的 mode 字段属于 SI 的"附加元数据"，与 ISO/IEC 11179-3 第 6.4 条 Item / Identified_Item 的属性变更需可追溯的精神一致。具体生成策略类型（UUID / SEQUENCE / TEMPLATE / SEMVER / DATE 等）由命名空间统一承载，本变更不重复持久化。
- **不做的事**：
  - 不在 SI 上记录具体生成策略类型（UUID / URN / SEQUENCE / TEMPLATE / SEMVER / SEQUENTIAL / DATE）—— 这些属于命名空间级配置，全局统一；
  - 不改动命名空间策略（`identifierInputMode` / `versionInputMode`）；
  - 不改动既有标识符生成策略的语义（UUID / SEQUENCE / 段模板）；
  - 不引入"创建后变更 mode"的反向能力——一旦创建，mode 即固化；
  - 不在响应体里直接暴露完整 `IdentifierStrategyConfig`（避免配置外泄）。