# active-registrar-ownership 增量规格

## MODIFIED Requirements

### Requirement: 注册权限与所有权归属

注册 Administered_Item 的权限对两类账号开放：Registrar 与 Submitter（已登记为 Contact 的注册用户）；既非 Registrar 也非 Contact 的账号 MUST NOT 注册 Administered_Item。Registrar 注册的 Administered_Item MUST 关联其活动 Registrar 身份所在的 Registration_Authority；Submitter（无活动 Registrar）注册的 Administered_Item MUST 归属系统唯一的注册机构（单注册表模式下由注册表隐式推导）。任何新建的 Administered_Item 的 `registration_authority_id` MUST NOT 为空。在注册机构自有命名空间下创建条目时，命名空间所属机构与活动身份所在机构不一致的，系统 SHALL 拒绝。在外部命名空间（无关联注册机构）下通过导入或手工编目创建的条目，同样 SHALL 归属活动 Registrar 身份所在的注册机构；外部命名空间本身 MUST NOT 关联任何注册机构，其 Naming_Authority SHALL 记录该外部标准的真实来源机构。请求体 MUST NOT 接受 `registrationAuthorityId` 或等效字段。

#### Scenario: Registrar 在自有命名空间注册条目

- **WHEN** 用户以活动 Registrar 身份在其所在注册机构 A 的命名空间下创建数据元
- **THEN** 系统 SHALL 创建该条目并将其归属注册机构设为 A

#### Scenario: 非 Registrar 注册被拒绝

- **WHEN** 没有任何 Registrar 身份也未登记为 Contact 的账号（包括团队版未自指派 Registrar 且未登记联系人的 Admin）尝试创建 Administered_Item
- **THEN** 系统 SHALL 拒绝该请求，并提示需先登记联系人身份或取得 Registrar 身份

#### Scenario: Submitter 注册条目归属系统唯一注册机构

- **WHEN** 已登记为 Contact 但无活动 Registrar 身份的账号创建 Administered_Item
- **THEN** 系统 SHALL 创建该条目并将其归属注册机构设为系统唯一注册机构；当系统不存在唯一注册机构时 SHALL 拒绝并报既有归属错误

#### Scenario: 跨机构写入被拒绝

- **WHEN** 用户的活动 Registrar 身份属于注册机构 A，尝试在注册机构 B 的自有命名空间下创建条目
- **THEN** 系统 SHALL 拒绝该请求并返回业务校验错误

#### Scenario: 外部命名空间编目条目归属本机构

- **WHEN** 用户以活动 Registrar 身份（所在注册机构 A）在外部命名空间下导入或手工编目外部组织的数据标准
- **THEN** 系统 SHALL 创建归属注册机构 A 的条目；该外部命名空间保持不关联任何注册机构，其 Naming_Authority 记录外部标准的真实来源机构

#### Scenario: 非 Registrar 在外部命名空间编目被拒绝

- **WHEN** 没有任何 Registrar 身份也未登记为 Contact 的账号尝试在外部命名空间下创建条目
- **THEN** 系统 SHALL 拒绝该请求，并提示需先登记联系人身份或取得 Registrar 身份
