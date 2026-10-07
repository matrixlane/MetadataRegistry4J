# Active Registrar Ownership Delta Specification

## MODIFIED Requirements

### Requirement: 活动 Registrar 硬约束

账号进行注册工作（注册受管项、生成或编辑 `Scoped_Identifier`、编辑标识/版本生成策略、推进注册状态）前，系统 MUST 要求该账号已激活一个且仅一个 Registrar 身份。激活状态 SHALL 保存在浏览器端（客户端本地存储），随请求以 `X-Active-Registrar-Id` 头传递给服务端；服务端解析目标身份 = 请求头所选身份（属于当前账号且关联注册机构，否则忽略）→ 账号默认注册人员身份（`ext.registrar.is_default`，见"默认注册人员标志"）→ 首个有效 Registrar 身份。解析 MUST 为纯读，MUST NOT 写回任何持久化缓存或状态。注册人员身份必有默认（首个 Registrar 身份自动设为默认）。不具有任何 Registrar 身份的账号（含 SUPER_ADMIN）MUST NOT 执行上述注册工作，但已登记为 Contact 的 Submitter 账号 SHALL 有权提交元数据（创建/编辑条目内容），其行为约束由"注册权限与所有权归属"与"变更权限约束"规约。系统 MUST NOT 要求用户显式选择激活身份。

#### Scenario: 单一身份自动激活

- **WHEN** 仅拥有一个 Registrar 身份的账号使用系统
- **THEN** 系统自动解析并激活该身份，账号可直接进行注册工作

#### Scenario: 多身份必须显式选择

- **WHEN** 拥有多个 Registrar 身份的账号发起请求且浏览器端未选择激活身份
- **THEN** 系统按账号默认注册人员身份自动激活（默认缺失时回退首个有效身份），无需任何显式选择即可进行注册工作

#### Scenario: 变更默认身份后激活跟随

- **WHEN** 用户将另一个 Registrar 身份设为默认后，在未携带浏览器端激活选择的会话（新浏览器/清空本地存储）中再次发起请求
- **THEN** 系统激活新默认注册人员身份；激活跟随仅由默认标志驱动，无任何持久化写回

#### Scenario: 无 Registrar 身份的 Submitter 可提交元数据但不可治理状态

- **WHEN** 无任何 Registrar 身份但已登记为 Contact 的账号尝试创建或编辑条目内容
- **THEN** 系统允许其提交（Submitter 通道），但推进注册状态、生成标识、编辑标识/版本策略的操作仍被拒绝

#### Scenario: 无 Registrar 身份禁止注册

- **WHEN** 没有任何 Registrar 身份也未登记为 Contact 的账号（包括团队版未自指派 Registrar 且未登记联系人的 Admin）尝试创建 Administered_Item
- **THEN** 系统 SHALL 拒绝该请求，并提示需先登记联系人身份或取得 Registrar 身份；该账号仅能查看条目详情

#### Scenario: 无 Registrar 身份的 Submitter 提交元数据但不可治理状态

- **WHEN** 无任何 Registrar 身份但已登记为 Contact 的账号尝试创建或编辑条目内容
- **THEN** 系统允许其提交（Submitter 通道），但推进注册状态、生成标识、编辑标识/版本策略的操作仍被拒绝

### Requirement: 活动 Registrar 身份解析

系统 SHALL 在服务端依据当前登录账号与其浏览器端激活选择解析其 Registrar 身份：身份列表由账号关联的 Contact 中的 Registrar 实例确定，活动注册机构由活动 Registrar 身份派生。解析目标 = 请求头所选身份（校验通过时）→ 默认注册人员身份 → 首个有效身份；存在唯一身份时该身份自动激活。解析 MUST NOT 依赖客户端传入的注册机构标识，且 MUST NOT 在解析过程中修改任何持久化状态。

#### Scenario: 唯一 Registrar 身份自动激活

- **WHEN** 某账号仅持有一个 Registrar 身份并使用系统
- **THEN** 系统 SHALL 自动以该身份为其活动 Registrar 身份，无需用户选择

#### Scenario: 默认注册人员优先于首个身份

- **WHEN** 账号持有多个 Registrar 身份、请求未携带有效激活选择，且默认注册人员身份已设置
- **THEN** 系统 SHALL 以默认注册人员身份为活动身份

#### Scenario: 无 Registrar 身份

- **WHEN** 某账号没有任何 Registrar 身份并发起注册 Administered_Item 的请求
- **THEN** 系统 SHALL 拒绝该请求，并提示需先取得 Registrar 身份

### Requirement: 活动 Registrar 身份唯一性、选择与切换

用户可同时持有多个 Registrar 身份（包括被其他注册机构授权的情形），但同一时刻 SHALL 仅有一个活动 Registrar 身份，所有注册工作均以该活动身份进行。激活选择 SHALL 保存在浏览器端（按浏览器维度的本地状态），系统 MUST NOT 提供持久化激活状态的写 API；用户可在使用中随时切换，切换 MUST NOT 修改服务端持久化状态。用户变更默认注册人员身份后，未携带激活选择的后续会话（新浏览器/清空本地存储）MUST 自动落回新默认身份。已选身份失效（Registrar 身份被撤销或不属于该账号）时，系统 SHALL 回退到默认注册人员身份或首个有效身份。

#### Scenario: 多身份用户登录后选择

- **WHEN** 同时持有注册机构 A 与 B 的 Registrar 身份的用户使用系统且浏览器端未选择激活身份
- **THEN** 系统 SHALL 按默认注册人员身份自动激活，无需先选择即可进行注册工作

#### Scenario: 切换活动身份

- **WHEN** 用户将浏览器端激活 Registrar 身份从机构 A 的身份切换为机构 B 的身份
- **THEN** 该浏览器的后续请求以机构 B 身份进行，切换即时生效且 MUST NOT 产生任何服务端写操作；其他浏览器/设备的激活状态不受影响

#### Scenario: 已选身份失效回退

- **WHEN** 用户浏览器端选择的激活 Registrar 身份被撤销或其注册机构被删除
- **THEN** 系统 SHALL 回退到该账号默认注册人员身份（缺失时回退剩余身份中的首个有效 Registrar 身份）

### Requirement: 前端归属展示与身份切换

条目创建与编辑表单 MUST NOT 提供注册机构选择控件；表单 SHALL 只读展示由服务端推导的归属注册机构。持有多个 Registrar 身份的用户 SHALL 在界面中获得活动身份的选择与切换入口，界面 SHALL 展示当前生效的活动 Registrar 身份及其所在注册机构；切换 SHALL 仅更新浏览器端激活状态并即时生效。

#### Scenario: 创建表单只读归属

- **WHEN** 用户以活动 Registrar 身份打开条目创建表单
- **THEN** 表单 SHALL 以只读方式展示活动身份所在注册机构作为条目归属，且不包含可编辑的注册机构选择器

#### Scenario: 多身份用户切换入口

- **WHEN** 持有多个 Registrar 身份的用户使用系统
- **THEN** 界面 SHALL 提供活动 Registrar 身份的选择与切换入口，并展示当前生效的活动身份

### Requirement: 活动身份只读展示

账号激活 Registrar 身份后，界面顶栏 SHALL 展示当前注册机构名称标签；无活动身份时 MUST NOT 展示该标识。账号持有多个 Registrar 身份时，该标签 SHALL 可点击弹出切换菜单：菜单 MUST 仅列出当前用户担任注册人员的注册机构（其 Registrar 身份），选择其中一项后系统 SHALL 把该选择记入浏览器端本地存储并使界面随之更新，MUST NOT 触发任何服务端写操作；单一身份或无身份时 MUST NOT 提供切换菜单。

#### Scenario: 顶栏只读展示

- **WHEN** 账号持有多个 Registrar 身份并已自动激活其一
- **THEN** 顶栏展示当前注册机构名称的可点击标签，点击弹出切换菜单且菜单仅含本人担任注册人员的机构

#### Scenario: 切换不发服务端请求

- **WHEN** 用户在顶栏切换菜单中选择另一 Registrar 身份
- **THEN** 界面立即以新身份展示；切换动作本身 MUST NOT 调用任何持久化端点，服务端仅在该浏览器后续请求中通过请求头感知新身份

#### Scenario: 无身份不展示

- **WHEN** 账号无任何 Registrar 身份
- **THEN** 顶栏不展示该标识

## ADDED Requirements

### Requirement: 默认注册人员标志

系统 SHALL 在 ext schema 卫星表（与 `mdr.registrar` 按 id 一一对应）上以 `is_default` 标志标识每个账号的一系列 Registrar 身份中的默认者；该标志 MUST NOT 存放于 mdr 标准表或 account 表。同一账号绑定的 Registrar 中 MUST 至多一个为默认；设置默认时系统 MUST 在同一事务内清除该账号其他 Registrar 的默认标志。账号的首个 Registrar 身份创建时 SHALL 自动标记为默认。用户 SHALL 通过"我的"联系人身份入口将本人任一 Registrar 身份设为默认；普通 Contact 身份 MUST NOT 可被设为默认（请求返回业务校验错误）。默认注册人员身份是激活解析的回退目标（详见"活动 Registrar 硬约束"），也是提交信息默认预填的来源（详见 `workspace` 能力）。删除默认 Registrar 身份后，系统 SHALL 自动把该账号剩余 Registrar 中的首个设为默认；删除最后一个 Registrar 后该账号无默认注册人员。存量数据 MUST 由迁移回填：优先取原 `account.active_registrar_id`，其次取原默认绑定对应的 Registrar，最后取账号首个 Registrar。

#### Scenario: 首个 Registrar 自动默认

- **WHEN** 通过"我的"入口、初始化向导或管理员指派等任一路径，为尚无 Registrar 身份的账号创建第一个 Registrar 身份
- **THEN** 该 Registrar 自动标记为默认注册人员

#### Scenario: 设置默认 Registrar

- **WHEN** 用户在"我的"入口将其某个 Registrar 身份设为默认
- **THEN** 该身份成为默认注册人员，同账号其他 Registrar 的默认标志在同一事务内被清除

#### Scenario: 普通 Contact 设默认被拒绝

- **WHEN** 用户将其普通联系人身份（非 Registrar）设为默认
- **THEN** 系统拒绝并返回业务校验错误，说明默认身份仅可设为注册人员身份

#### Scenario: 删除默认 Registrar 自动递补

- **WHEN** 用户删除当前默认的 Registrar 身份，且账号仍有其他 Registrar 身份
- **THEN** 系统将该账号剩余 Registrar 中的首个自动设为默认

#### Scenario: 迁移回填优先级

- **WHEN** 早于本能力的存量库执行迁移
- **THEN** 每账号至多一个 Registrar 被标默认：原 `active_registrar_id` 有效则取其；否则取原默认绑定对应的 Registrar；再否则取该账号首个 Registrar
