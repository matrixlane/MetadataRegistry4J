# Active Registrar Ownership Delta Specification

## MODIFIED Requirements

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
