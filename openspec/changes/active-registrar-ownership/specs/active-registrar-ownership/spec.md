## ADDED Requirements

### Requirement: 活动 Registrar 身份解析

系统 SHALL 在服务端依据当前登录账号解析其 Registrar 身份：通过账号关联的 Contact 中的 Registrar 实例确定身份列表，活动注册机构由活动 Registrar 身份派生。存在唯一身份时该身份自动激活。活动身份的解析 MUST NOT 依赖客户端传入的身份或注册机构标识。

#### Scenario: 唯一 Registrar 身份自动激活

- **WHEN** 某账号仅持有一个 Registrar 身份并登录系统
- **THEN** 系统 SHALL 自动以该身份为其活动 Registrar 身份，无需用户选择

#### Scenario: 无 Registrar 身份

- **WHEN** 某账号没有任何 Registrar 身份并发起注册 Administered_Item 的请求
- **THEN** 系统 SHALL 拒绝该请求，并提示需先取得 Registrar 身份

### Requirement: 活动 Registrar 身份唯一性、选择与切换

用户可同时持有多个 Registrar 身份（包括被其他注册机构授权的情形），但同一时刻 SHALL 仅有一个活动 Registrar 身份，所有注册工作均以该活动身份进行。持有多个身份的用户登录后 SHALL 选择一个活动身份，可在使用中随时切换；选择 MUST 持久化到该账号，已选身份失效（Registrar 身份被撤销）时系统 SHALL 回退到首个有效身份。

#### Scenario: 多身份用户登录后选择

- **WHEN** 同时持有注册机构 A 与 B 的 Registrar 身份的用户登录系统
- **THEN** 系统 SHALL 要求其选择一个活动 Registrar 身份后方可进行注册工作

#### Scenario: 切换活动身份

- **WHEN** 用户将活动 Registrar 身份从机构 A 的身份切换为机构 B 的身份
- **THEN** 系统 SHALL 持久化该选择，后续注册工作均以机构 B 的身份进行

#### Scenario: 已选身份失效回退

- **WHEN** 用户已选的活动 Registrar 身份被撤销
- **THEN** 系统 SHALL 回退到该账号剩余身份中的首个有效 Registrar 身份

### Requirement: 注册权限与所有权归属

非 Registrar 没有权限注册 Administered_Item；Registrar 有权限注册 Administered_Item，且所注册的 Administered_Item MUST 关联其活动 Registrar 身份所在的 Registration_Authority。在注册机构自有命名空间下创建条目时，命名空间所属机构与活动身份所在机构不一致的，系统 SHALL 拒绝。在外部命名空间（无关联注册机构、非 system-default）下通过导入或手工编目创建的条目，同样 SHALL 归属活动 Registrar 身份所在的注册机构；外部命名空间本身 MUST NOT 关联任何注册机构，其 Naming_Authority SHALL 记录该外部标准的真实来源机构。请求体 MUST NOT 接受 `registrationAuthorityId` 或等效字段。

#### Scenario: Registrar 在自有命名空间注册条目

- **WHEN** 用户以活动 Registrar 身份在其所在注册机构 A 的命名空间下创建数据元
- **THEN** 系统 SHALL 创建该条目并将其归属注册机构设为 A

#### Scenario: 非 Registrar 注册被拒绝

- **WHEN** 没有任何 Registrar 身份的用户（包括团队版未自指派 Registrar 的 Admin）尝试创建 Administered_Item
- **THEN** 系统 SHALL 拒绝该请求，并提示需先取得 Registrar 身份

#### Scenario: 跨机构写入被拒绝

- **WHEN** 用户的活动 Registrar 身份属于注册机构 A，尝试在注册机构 B 的自有命名空间下创建条目
- **THEN** 系统 SHALL 拒绝该请求并返回业务校验错误

#### Scenario: 外部命名空间编目条目归属本机构

- **WHEN** 用户以活动 Registrar 身份（所在注册机构 A）在外部命名空间下导入或手工编目外部组织的数据标准
- **THEN** 系统 SHALL 创建归属注册机构 A 的条目；该外部命名空间保持不关联任何注册机构，其 Naming_Authority 记录外部标准的真实来源机构

#### Scenario: 非 Registrar 在外部命名空间编目被拒绝

- **WHEN** 没有任何 Registrar 身份的用户尝试在外部命名空间下创建条目
- **THEN** 系统 SHALL 拒绝该请求，并提示需先取得 Registrar 身份

### Requirement: 变更权限约束

修改或删除已归属注册机构的管理项时，当前用户 MUST 正以该注册机构的 Registrar 身份为活动身份；仅持有该机构 Registrar 身份但未激活的，须先切换活动身份。无归属的管理项仅可能为历史遗留数据（新注册条目一律有归属），维持既有校验规则。超级管理员 SHALL NOT 豁免此约束，但可通过既有 Registrar 指派能力先获得身份。

#### Scenario: 以对应机构活动身份修改条目

- **WHEN** 条目归属注册机构 A，当前用户正以机构 A 的 Registrar 身份为活动身份并提交修改
- **THEN** 系统 SHALL 允许该修改

#### Scenario: 持有身份但未激活时修改被拒绝

- **WHEN** 条目归属注册机构 A，用户持有机构 A 的 Registrar 身份但当前活动身份属于机构 B，提交修改
- **THEN** 系统 SHALL 拒绝该请求，并提示先切换活动 Registrar 身份

#### Scenario: 非本机构 Registrar 修改被拒绝

- **WHEN** 条目归属注册机构 A，当前用户不持有机构 A 的任何 Registrar 身份并提交修改
- **THEN** 系统 SHALL 拒绝该请求并返回权限错误

### Requirement: 联邦并入条目所有权保持

通过联邦合并并入的管理项 SHALL 保留其来源注册机构的归属；本地任何创建、更新或合并流程 MUST NOT 将并入条目的归属改写为本地注册机构。对已建立 `item_link` 链接的本地既有条目，合并负载 MUST NOT 覆盖其 `registrationAuthority`。

#### Scenario: 并入条目保留来源归属

- **WHEN** 目标节点并入来自源节点的合并包，其中条目归属源注册机构 X
- **THEN** 目标端 SHALL 保持该条目归属 X 在本地的表示，而非目标节点的本地注册机构

#### Scenario: 已链接条目归属不被覆盖

- **WHEN** 后续合并请求到达，其负载中的条目已通过 `item_link` 链接到归属本地注册机构 A 的既有条目
- **THEN** 系统 SHALL NOT 用负载内容改写该条目的归属注册机构

### Requirement: 前端归属展示与身份切换

条目创建与编辑表单 MUST NOT 提供注册机构选择控件；表单 SHALL 只读展示由服务端推导的归属注册机构。持有多个 Registrar 身份的用户 SHALL 在界面中获得活动身份的选择与切换入口，界面 SHALL 展示当前生效的活动 Registrar 身份及其所在注册机构。

#### Scenario: 创建表单只读归属

- **WHEN** 用户以活动 Registrar 身份打开条目创建表单
- **THEN** 表单 SHALL 以只读方式展示活动身份所在注册机构作为条目归属，且不包含可编辑的注册机构选择器

#### Scenario: 多身份用户切换入口

- **WHEN** 持有多个 Registrar 身份的用户登录系统
- **THEN** 界面 SHALL 提供活动 Registrar 身份的选择与切换入口，并展示当前生效的活动身份
