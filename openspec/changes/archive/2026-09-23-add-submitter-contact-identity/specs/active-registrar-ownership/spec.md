# active-registrar-ownership 增量（add-submitter-contact-identity）

## MODIFIED Requirements

### Requirement: 活动 Registrar 硬约束

账号进行注册工作（注册受管项、生成或编辑 `Scoped_Identifier`、编辑标识/版本生成策略、推进注册状态）前，系统 MUST 要求该账号已激活一个且仅一个 Registrar 身份。账号仅有一个 Registrar 身份时 SHALL 自动激活；拥有多个 Registrar 身份时 MUST 显式选择其一后方可继续；不具有任何 Registrar 身份的账号（含 SUPER_ADMIN）MUST NOT 执行上述注册工作，但已登记为 Contact 的 Submitter 账号 SHALL 有权提交元数据（创建/编辑条目内容），其行为约束由"注册权限与所有权归属"与"变更权限约束"规约。

#### Scenario: 单一身份自动激活

- **WHEN** 仅拥有一个 Registrar 身份的账号登录系统
- **THEN** 系统自动激活该身份，账号可直接进行注册工作

#### Scenario: 多身份必须显式选择

- **WHEN** 拥有两个以上 Registrar 身份的账号登录后尝试进入注册相关功能
- **THEN** 系统要求其先选择激活一个 Registrar 身份，未选择前阻止注册操作并展示选择引导

#### Scenario: 无 Registrar 身份的 Submitter 可提交元数据但不可治理状态

- **WHEN** 无任何 Registrar 身份但已登记为 Contact 的账号尝试创建或编辑条目内容
- **THEN** 系统允许其提交（Submitter 通道），但推进注册状态、生成标识、编辑标识/版本策略的操作仍被拒绝

#### Scenario: 无 Registrar 身份禁止注册

- **WHEN** 没有任何 Registrar 身份也未登记为 Contact 的账号（包括团队版未自指派 Registrar 且未登记联系人的 Admin）尝试创建 Administered_Item
- **THEN** 系统 SHALL 拒绝该请求，并提示需先登记联系人身份或取得 Registrar 身份；该账号仅能查看条目详情

#### Scenario: 无 Registrar 身份的 Submitter 提交元数据但不可治理状态

- **WHEN** 无任何 Registrar 身份但已登记为 Contact 的账号尝试创建或编辑条目内容
- **THEN** 系统允许其提交（Submitter 通道），但推进注册状态、生成标识、编辑标识/版本策略的操作仍被拒绝

### Requirement: 注册权限与所有权归属

注册 Administered_Item 的权限对两类账号开放：Registrar 与 Submitter（已登记为 Contact 的注册用户）；既非 Registrar 也非 Contact 的账号 MUST NOT 注册 Administered_Item。Registrar 注册的 Administered_Item MUST 关联其活动 Registrar 身份所在的 Registration_Authority；Submitter（无活动 Registrar）注册的 Administered_Item MUST 归属系统唯一的注册机构（单注册表模式下由注册表隐式推导）。任何新建的 Administered_Item 的 `registration_authority_id` MUST NOT 为空。在注册机构自有命名空间下创建条目时，命名空间所属机构与活动身份所在机构不一致的，系统 SHALL 拒绝。在外部命名空间（无关联注册机构、非 system-default）下通过导入或手工编目创建的条目，同样 SHALL 归属活动 Registrar 身份所在的注册机构；外部命名空间本身 MUST NOT 关联任何注册机构，其 Naming_Authority SHALL 记录该外部标准的真实来源机构。请求体 MUST NOT 接受 `registrationAuthorityId` 或等效字段。

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

### Requirement: 变更权限约束

修改或删除已归属注册机构的管理项时，当前用户 MUST 满足以下任一条件：正以该注册机构的 Registrar 身份为活动身份；或为 Submitter（已登记为 Contact）且该条目归属系统唯一的注册机构。仅持有该机构 Registrar 身份但未激活的，须先切换活动身份。无归属的管理项仅可能为历史遗留数据（新注册条目一律有归属），维持既有校验规则。超级管理员 SHALL NOT 豁免此约束，但可通过既有 Registrar 指派能力先获得身份。

#### Scenario: 以对应机构活动身份修改条目

- **WHEN** 条目归属注册机构 A，当前用户正以机构 A 的 Registrar 身份为活动身份并提交修改
- **THEN** 系统 SHALL 允许该修改

#### Scenario: 持有身份但未激活时修改被拒绝

- **WHEN** 条目归属注册机构 A，用户持有机构 A 的 Registrar 身份但当前活动身份属于机构 B，提交修改
- **THEN** 系统 SHALL 拒绝该请求，并提示先切换活动 Registrar 身份

#### Scenario: 非本机构 Registrar 修改被拒绝

- **WHEN** 条目归属注册机构 A，当前用户既不持有机构 A 的 Registrar 身份也未登记为 Contact，并提交修改
- **THEN** 系统 SHALL 拒绝该请求并返回权限错误

#### Scenario: Submitter 修改归属本系统注册机构的条目

- **WHEN** 条目归属系统唯一注册机构，当前用户为 Submitter（已登记为 Contact、无活动 Registrar 身份）并提交修改或删除
- **THEN** 系统 SHALL 允许该操作
