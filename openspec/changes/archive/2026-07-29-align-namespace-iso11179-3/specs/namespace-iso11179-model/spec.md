## Requirements

### REQ-NS-01 Namespace 必须正确关联 naming_authority
`Namespace` SHALL 通过 `naming_authority_id` 外键关联到 `Organization`，而不是存储字符串。

#### Scenario: 创建 Namespace 时指定 naming_authority
- **WHEN** 调用方提供有效的 `namingAuthorityId`
- **THEN** 系统保存 Namespace 并将 `naming_authority_id` 指向对应 Organization

#### Scenario: 未指定 naming_authority
- **WHEN** 调用方未提供 `namingAuthorityId`
- **THEN** 系统允许创建 Namespace，`naming_authority_id` 为 NULL

### REQ-NS-02 registration_authority_namespace 双向可选关联必须保留
ISO/IEC 11179-3:2023 Clause 9.5.5 定义 `registration_authority_namespace` 为 RegistrationAuthority 与其 Namespace 之间的可选关联。`Namespace.registrationAuthority` 与 `RegistrationAuthority.namespace` 均为 `0..1`，双向导航均保留；DTO 侧不暴露 `registrationAuthorityId`，避免误读为“RA 管辖 Namespace”。

#### Scenario: 从 RA 查询其 Namespace
- **WHEN** 读取 `RegistrationAuthority`
- **THEN** 返回其 `namespace` 字段，语义为“该 RA 的标识符受此 Namespace 约束”

#### Scenario: 从 Namespace 实体可导航到 RA
- **WHEN** 读取 `Namespace` 实体
- **THEN** 可通过 `registrationAuthority` 访问其 RA（可能为 null）

#### Scenario: Namespace 响应不包含 registrationAuthorityId
- **WHEN** 读取 `NamespaceResponse` 或 `NamespaceDetailResponse`
- **THEN** 字段中不存在 `registrationAuthorityId`

### REQ-NS-03 Namespace 治理属性必须完整暴露
`NamespaceRequest`、`NamespaceResponse`、`NamespaceDetailResponse` SHALL 暴露 `shorthandPrefix`、`schemeReference`、`mandatoryNamingConventionIndicator`、`oneNamePerItemIndicator`、`oneItemPerNameIndicator`、`namingAuthorityId`、`namingConventionIds`、`parentId`。

#### Scenario: 普通 Namespace 创建携带完整治理属性
- **WHEN** 调用普通 `POST /api/namespaces` 并传入所有治理属性
- **THEN** 系统保存并返回完整治理属性，行为与 Wizard 入口一致

### REQ-NS-04 NamespaceStatus 必须完全移除
`NamespaceStatus` 不是 ISO/IEC 11179-3 标准属性，必须完全移除：删除 `NamespaceStatus.java` 枚举、`Namespace.namespaceStatus` 字段、`NamespaceStateMachine` 服务及测试，从 DTO 中移除 `namespaceStatus`，并通过 Flyway 删除数据库 `namespace_status` 列。Namespace 生命周期统一由 `AdministeredItem → RegistrationState` 表达。

#### Scenario: Namespace 中不存在 namespaceStatus
- **WHEN** 开发者阅读 `Namespace.java`
- **THEN** 不存在 `namespaceStatus` 字段

#### Scenario: 数据库中不存在 namespace_status 列
- **WHEN** 执行 Flyway 迁移
- **THEN** `namespace` 表中不存在 `namespace_status` 列

#### Scenario: 查看实体 Javadoc
- **WHEN** 开发者阅读 `Namespace.java`
- **THEN** Javadoc 引用标准生命周期路径 `AdministeredItem → RegistrationState`
