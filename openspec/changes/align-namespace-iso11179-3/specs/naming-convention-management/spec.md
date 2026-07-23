## Requirements

### REQ-NC-01 NamingConvention 必须可独立创建与管理
系统 SHALL 提供 `NamingConventionController`，支持 `POST /api/v1/naming-conventions`、`GET /api/v1/naming-conventions`、`GET /api/v1/naming-conventions/{id}`、`PUT /api/v1/naming-conventions/{id}`、`DELETE /api/v1/naming-conventions/{id}`。

#### Scenario: 创建 NamingConvention
- **WHEN** 调用方提供 `scopeRule`（必需）与 `authorityRule`（可选）以及至少一个 designation
- **THEN** 系统创建 `NamingConvention` 实体并返回其 ID 与字段

#### Scenario: 查询 NamingConvention 列表
- **WHEN** 调用方请求 NamingConvention 列表
- **THEN** 系统返回分页结果，包含 `scopeRule`、`authorityRule`、`id`、首选 designation

### REQ-NC-02 Namespace 必须能关联多个 NamingConvention
`Namespace` 与 `NamingConvention` 之间 SHALL 通过 `namespace_naming_convention` 连接表建立多对多关联，由 `NamespaceService.applyRequest` 解析 `namingConventionIds` 并维护。

#### Scenario: 创建 Namespace 时关联 NamingConvention
- **WHEN** 调用方传入一组有效的 `namingConventionIds`
- **THEN** 系统保存 Namespace 并将其与该组 NamingConvention 关联

#### Scenario: 更新 Namespace 时移除所有 NamingConvention
- **WHEN** 调用方传入空 `namingConventionIds`
- **THEN** 系统清空该 Namespace 的所有 NamingConvention 关联

### REQ-NC-03 Designation 必须能声明符合的 NamingConvention
`Designation` 与 `NamingConvention` 之间 SHALL 通过 `designation_naming_convention` 连接表建立多对多关联（`naming_convention_conformance`）。

#### Scenario: 创建或更新 Designation 时指定 NamingConvention
- **WHEN** 调用方在 Designation 中传入 `namingConventionIds`
- **THEN** 系统保存 Designation 与对应 NamingConvention 的关联

### REQ-NC-04 NamingConvention 必须继承 Item 的全部能力
`NamingConvention` SHALL 继承 `Item`，因此可被命名、定义、识别、注册、管理。

#### Scenario: NamingConvention 支持 designation 和 definition
- **WHEN** 调用方创建 NamingConvention 时提供 designation 和 definition
- **THEN** 系统按 `ItemAdministrationService` 标准流程保存 designation 与 definition
