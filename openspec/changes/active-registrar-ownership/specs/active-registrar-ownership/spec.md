## ADDED Requirements

### Requirement: 活动注册员解析

系统 SHALL 在服务端依据当前登录账号解析活动注册员：通过账号关联的 Contact 中的 Registrar 实例确定注册员绑定；存在唯一绑定时，该绑定的注册机构即为活动注册机构。无任何 Registrar 绑定的账号 SHALL 被视为无活动注册机构。活动注册机构的解析 MUST NOT 依赖客户端传入的注册机构标识。

#### Scenario: 唯一注册员绑定

- **WHEN** 某账号仅持有一个注册机构的 Registrar 绑定并发起需要活动注册机构的操作
- **THEN** 系统 SHALL 将该注册机构作为活动注册机构，无需用户选择

#### Scenario: 无注册员绑定

- **WHEN** 某账号没有任何 Registrar 绑定并发起创建自有数据标准的请求
- **THEN** 系统 SHALL 判定该账号无活动注册机构，并拒绝该请求

### Requirement: 多注册机构下的活动注册机构选择

当账号同时持有多个注册机构的 Registrar 绑定时，系统 SHALL 提供活动注册机构的选择与切换能力，并将选择持久化到该账号；未做选择或已选机构失效（注册员身份被撤销）时，系统 SHALL 回退到首个有效绑定。

#### Scenario: 切换活动注册机构

- **WHEN** 持有多个注册机构 Registrar 绑定的用户将活动注册机构从 A 切换为 B
- **THEN** 系统 SHALL 持久化该选择，后续操作的活动注册机构为 B

#### Scenario: 已选机构失效回退

- **WHEN** 用户已选活动注册机构为 A，但其对 A 的 Registrar 绑定被撤销
- **THEN** 系统 SHALL 回退到该账号剩余绑定中的首个有效注册机构

### Requirement: 创建时所有权自动归属

在注册机构自有命名空间下创建管理项时，系统 SHALL 由服务端将条目的 `registrationAuthority` 设为该命名空间所属注册机构，且当前用户的活动注册机构 MUST 与该机构一致；请求体 MUST NOT 接受 `registrationAuthorityId` 或等效字段。在外部命名空间（无关联注册机构、非 system-default）下编目外部标准时，条目 SHALL 保持无注册机构归属，且不要求注册员身份。

#### Scenario: 注册员在自有命名空间创建条目

- **WHEN** 注册员在活动注册机构 A 的命名空间下创建数据元
- **THEN** 系统 SHALL 将该条目的归属注册机构设为 A

#### Scenario: 跨机构写入被拒绝

- **WHEN** 当前用户的活动注册机构为 A，尝试在注册机构 B 的自有命名空间下创建条目
- **THEN** 系统 SHALL 拒绝该请求并返回业务校验错误

#### Scenario: 外部命名空间编目

- **WHEN** 已认证用户在外部命名空间下编目外部组织的数据标准
- **THEN** 系统 SHALL 创建无注册机构归属的条目，且不要求注册员身份

### Requirement: 变更权限约束

修改或删除已归属注册机构的管理项时，当前用户 MUST 持有该注册机构的有效 Registrar 绑定；无归属的管理项维持既有校验规则。超级管理员 SHALL NOT 豁免此约束，但可通过既有注册员指派能力先获得身份。

#### Scenario: 本机构注册员修改条目

- **WHEN** 条目归属注册机构 A，当前用户持有 A 的 Registrar 绑定并提交修改
- **THEN** 系统 SHALL 允许该修改

#### Scenario: 非本机构注册员修改被拒绝

- **WHEN** 条目归属注册机构 A，当前用户仅持有注册机构 B 的 Registrar 绑定并提交修改
- **THEN** 系统 SHALL 拒绝该请求并返回权限错误

#### Scenario: 管理员无注册员身份亦被拒绝

- **WHEN** 超级管理员没有任何 Registrar 绑定，尝试修改归属注册机构 A 的条目
- **THEN** 系统 SHALL 拒绝该请求，并提示需先取得该机构的注册员身份

### Requirement: 联邦并入条目所有权保持

通过联邦合并并入的管理项 SHALL 保留其来源注册机构的归属；本地任何创建、更新或合并流程 MUST NOT 将并入条目的归属改写为本地注册机构。对已建立 `item_link` 链接的本地既有条目，合并负载 MUST NOT 覆盖其 `registrationAuthority`。

#### Scenario: 并入条目保留来源归属

- **WHEN** 目标节点并入来自源节点的合并包，其中条目归属源注册机构 X
- **THEN** 目标端 SHALL 保持该条目归属 X 在本地的表示，而非目标节点的本地注册机构

#### Scenario: 已链接条目归属不被覆盖

- **WHEN** 后续合并请求到达，其负载中的条目已通过 `item_link` 链接到归属本地注册机构 A 的既有条目
- **THEN** 系统 SHALL NOT 用负载内容改写该条目的归属注册机构

### Requirement: 前端归属展示

条目创建与编辑表单 MUST NOT 提供注册机构选择控件；表单 SHALL 只读展示由服务端推导的归属注册机构。持有多个注册机构 Registrar 绑定的用户 SHALL 在界面中获得活动注册机构的切换入口。

#### Scenario: 创建表单只读归属

- **WHEN** 注册员打开条目创建表单
- **THEN** 表单 SHALL 以只读方式展示活动注册机构作为条目归属，且不包含可编辑的注册机构选择器

#### Scenario: 多机构用户切换入口

- **WHEN** 持有多个注册机构 Registrar 绑定的用户登录系统
- **THEN** 界面 SHALL 提供活动注册机构的切换入口，并展示当前生效的活动注册机构
