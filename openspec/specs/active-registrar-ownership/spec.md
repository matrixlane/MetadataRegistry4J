# active-registrar-ownership Specification

## Purpose
定义"活动 Registrar"会话状态及其硬约束：一名用户可作为多个注册机构的 Registrar，但同一时间必须激活一个且仅一个 Registrar 身份才能进行注册工作。命名空间"自有/外部"的判定基于当前激活的 Registrar，该判定是标识生成闸门与命名空间设置分支的输入。受委托签发通过"授权组织外用户成为本注册机构下的 Registrar"表达，无需第三态。

## Requirements

### Requirement: 活动 Registrar 硬约束

账号进行注册工作（注册受管项、生成或编辑 `Scoped_Identifier`、编辑标识/版本生成策略、推进注册状态）前，系统 MUST 要求该账号已激活一个且仅一个 Registrar 身份。激活 SHALL **恒随账号的默认注册人员身份**（activation-follows-default）：每次解析的目标身份 MUST 为账号默认绑定对应的有效 Registrar 身份（默认绑定缺失或非 Registrar 时回退首个有效 Registrar 身份）；`active_registrar_id` 仅作同步缓存，与目标不一致时系统 SHALL 写回。用户变更默认注册人员身份后，下一次解析 MUST 自动跟随新默认身份激活。注册人员身份必有默认（首个身份自动设为默认）。不具有任何 Registrar 身份的账号（含 SUPER_ADMIN）MUST NOT 执行上述注册工作，但已登记为 Contact 的 Submitter 账号 SHALL 有权提交元数据（创建/编辑条目内容），其行为约束由"注册权限与所有权归属"与"变更权限约束"规约。系统 MUST NOT 要求用户显式选择激活身份。

#### Scenario: 单一身份自动激活

- **WHEN** 仅拥有一个 Registrar 身份的账号登录系统
- **THEN** 系统自动激活该身份，账号可直接进行注册工作

#### Scenario: 多身份必须显式选择

- **WHEN** 拥有多个 Registrar 身份的账号登录系统
- **THEN** 系统按账号默认注册人员身份自动激活（默认缺失时回退首个有效身份），无需任何显式选择即可进行注册工作

#### Scenario: 变更默认身份后激活跟随

- **WHEN** 用户将另一个 Registrar 身份设为默认后再次发起请求
- **THEN** 系统激活新默认身份（与持久化缓存不一致时写回），顶栏等处的活动机构展示随之更新

#### Scenario: 无 Registrar 身份的 Submitter 可提交元数据但不可治理状态

- **WHEN** 无任何 Registrar 身份但已登记为 Contact 的账号尝试创建或编辑条目内容
- **THEN** 系统允许其提交（Submitter 通道），但推进注册状态、生成标识、编辑标识/版本策略的操作仍被拒绝

#### Scenario: 无 Registrar 身份禁止注册

- **WHEN** 没有任何 Registrar 身份也未登记为 Contact 的账号（包括团队版未自指派 Registrar 且未登记联系人的 Admin）尝试创建 Administered_Item
- **THEN** 系统 SHALL 拒绝该请求，并提示需先登记联系人身份或取得 Registrar 身份；该账号仅能查看条目详情

#### Scenario: 无 Registrar 身份的 Submitter 提交元数据但不可治理状态

- **WHEN** 无任何 Registrar 身份但已登记为 Contact 的账号尝试创建或编辑条目内容
- **THEN** 系统允许其提交（Submitter 通道），但推进注册状态、生成标识、编辑标识/版本策略的操作仍被拒绝

### Requirement: 自有/外部命名空间判定

给定命名空间与当前激活 Registrar，系统 SHALL 判定：命名空间关联的注册机构非空且等于激活 Registrar 所属注册机构时，该命名空间为**自有**；否则为**外部**。判定 MUST 以 Registrar 实体的 `registrationAuthority` 关联为准。注册机构与自有命名空间为 1:1 关系（两边 0..1）。

#### Scenario: 自有判定

- **WHEN** 当前激活 Registrar 属于注册机构 R，目标命名空间关联的注册机构为 R
- **THEN** 判定结果为自有

#### Scenario: 外部判定

- **WHEN** 当前激活 Registrar 属于注册机构 R，目标命名空间关联的注册机构为其他机构或未关联任何注册机构
- **THEN** 判定结果为外部

#### Scenario: 判定不依赖角色名字符串

- **WHEN** 某 Contact 的角色名被修改为任意文本，但其 Registrar 实体的 RA 关联未变
- **THEN** 自有/外部判定结果不受角色名影响

### Requirement: 标识生成与策略编辑闸门

标识自动生成 MUST 仅在目标命名空间相对当前激活 Registrar 为自有时允许；对相对外部的命名空间，系统 MUST 拒绝自动生成，仅允许手动录入或导入。标识/版本生成策略的编辑 MUST 仅允许该命名空间所属注册机构的激活 Registrar。

#### Scenario: 自有命名空间放行生成

- **WHEN** 激活 Registrar 在其注册机构自有命名空间下注册元数据且未填标识
- **THEN** 系统按该命名空间已发布策略自动生成标识

#### Scenario: 外部命名空间拒绝生成

- **WHEN** 激活 Registrar 在外部命名空间提交未填标识的创建请求
- **THEN** 系统拒绝并提示外部命名空间必须手动录入标识

#### Scenario: 非所属机构身份不可编辑策略

- **WHEN** 激活 Registrar 尝试编辑非其所属注册机构命名空间的标识/版本策略
- **THEN** 系统拒绝该写操作

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

### Requirement: 本地路径归属冻结

本地创建、更新或删除流程 MUST NOT 修改既有条目的 `registrationAuthority`。条目的归属变更 SHALL 仅通过注册表合并发生：来源方与目的方注册机构的 RAI 一致时，迁移条目重新关联到目的方注册机构，其命名空间与标识的迁移语义由注册表合并策略（`registry-merge-strategy`）规约。

#### Scenario: 本地更新不改归属

- **WHEN** 用户以对应机构活动 Registrar 身份修改某条目的内容
- **THEN** 系统 SHALL 保持该条目的归属注册机构不变

#### Scenario: 归属变更仅经由合并

- **WHEN** 某条目的归属注册机构发生变化
- **THEN** 该变化 SHALL 仅由 RAI 一致的注册表合并流程按合并策略引发，而非本地编辑或 API 调用

### Requirement: 前端归属展示与身份切换

条目创建与编辑表单 MUST NOT 提供注册机构选择控件；表单 SHALL 只读展示由服务端推导的归属注册机构。持有多个 Registrar 身份的用户 SHALL 在界面中获得活动身份的选择与切换入口，界面 SHALL 展示当前生效的活动 Registrar 身份及其所在注册机构。

#### Scenario: 创建表单只读归属

- **WHEN** 用户以活动 Registrar 身份打开条目创建表单
- **THEN** 表单 SHALL 以只读方式展示活动身份所在注册机构作为条目归属，且不包含可编辑的注册机构选择器

#### Scenario: 多身份用户切换入口

- **WHEN** 持有多个 Registrar 身份的用户登录系统
- **THEN** 界面 SHALL 提供活动 Registrar 身份的选择与切换入口，并展示当前生效的活动身份

### Requirement: 活动身份只读展示

账号激活 Registrar 身份后，界面顶栏 SHALL 展示当前注册机构名称标签；无活动身份时 MUST NOT 展示该标识。账号持有多个 Registrar 身份时，该标签 SHALL 可点击弹出切换菜单：菜单 MUST 仅列出当前用户担任注册人员的注册机构（其 Registrar 身份），选择其中一项后系统 SHALL 切换激活身份并使界面随之更新；单一身份或无身份时 MUST NOT 提供切换菜单。

#### Scenario: 顶栏只读展示

- **WHEN** 账号持有多个 Registrar 身份并已自动激活其一
- **THEN** 顶栏展示当前注册机构名称的可点击标签，点击弹出切换菜单且菜单仅含本人担任注册人员的机构

#### Scenario: 无身份不展示

- **WHEN** 账号无任何 Registrar 身份
- **THEN** 顶栏不展示该标识
