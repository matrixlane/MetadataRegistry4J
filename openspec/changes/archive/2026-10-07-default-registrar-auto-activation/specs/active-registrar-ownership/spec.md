# Active Registrar Ownership Delta Specification

## MODIFIED Requirements

### Requirement: 活动 Registrar 硬约束

账号进行注册工作（注册受管项、生成或编辑 `Scoped_Identifier`、编辑标识/版本生成策略、推进注册状态）前，系统 MUST 要求该账号已激活一个且仅一个 Registrar 身份。激活 SHALL 按默认注册人员身份自动完成：账号持久化的活动身份仍有效时沿用之；否则系统 MUST 取账号默认绑定对应的 Registrar 身份自动激活并持久化（默认身份缺失时回退到首个有效 Registrar 身份）；注册人员身份必有默认（首个身份自动设为默认）。不具有任何 Registrar 身份的账号（含 SUPER_ADMIN）MUST NOT 执行上述注册工作，但已登记为 Contact 的 Submitter 账号 SHALL 有权提交元数据（创建/编辑条目内容），其行为约束由"注册权限与所有权归属"与"变更权限约束"规约。系统 MUST NOT 要求用户显式选择激活身份。

#### Scenario: 单一身份自动激活

- **WHEN** 仅拥有一个 Registrar 身份的账号登录系统
- **THEN** 系统自动激活该身份，账号可直接进行注册工作

#### Scenario: 多身份必须显式选择

- **WHEN** 拥有多个 Registrar 身份的账号登录系统
- **THEN** 系统按账号默认注册人员身份自动激活（默认缺失时回退首个有效身份），无需任何显式选择即可进行注册工作

#### Scenario: 无 Registrar 身份的 Submitter 可提交元数据但不可治理状态

- **WHEN** 无任何 Registrar 身份但已登记为 Contact 的账号尝试创建或编辑条目内容
- **THEN** 系统允许其提交（Submitter 通道），但推进注册状态、生成标识、编辑标识/版本策略的操作仍被拒绝

#### Scenario: 无 Registrar 身份禁止注册

- **WHEN** 没有任何 Registrar 身份也未登记为 Contact 的账号（包括团队版未自指派 Registrar 且未登记联系人的 Admin）尝试创建 Administered_Item
- **THEN** 系统 SHALL 拒绝该请求，并提示需先登记联系人身份或取得 Registrar 身份；该账号仅能查看条目详情

#### Scenario: 无 Registrar 身份的 Submitter 提交元数据但不可治理状态

- **WHEN** 无任何 Registrar 身份但已登记为 Contact 的账号尝试创建或编辑条目内容
- **THEN** 系统允许其提交（Submitter 通道），但推进注册状态、生成标识、编辑标识/版本策略的操作仍被拒绝

## REMOVED Requirements

### Requirement: Registrar 身份切换
**Reason**: 登录后不再需要显式选择激活身份；激活随默认注册人员身份自动完成，切换入口（顶栏下拉与选择对话框）已移除，`PUT /api/v1/active-registrar` 端点保留但无界面调用方。
**Migration**: 由 Requirement "活动 Registrar 硬约束"（多身份自动激活）替代；后续注册操作以默认身份记账。

#### Scenario: 切换身份生效

- **WHEN** 多身份账号在顶栏切换器中选择另一个 Registrar 身份
- **THEN** 活动身份立即更新，后续注册操作以新身份的注册机构归属记账

#### Scenario: 身份展示

- **WHEN** 账号已激活 Registrar 身份
- **THEN** 界面顶栏展示当前注册机构与 Registrar 名称

## ADDED Requirements

### Requirement: 活动身份只读展示

账号激活 Registrar 身份后，界面顶栏 SHALL 只读展示当前注册机构名称（不提供切换或选择交互）；无活动身份时 MUST NOT 展示该标识。

#### Scenario: 顶栏只读展示

- **WHEN** 账号已自动激活 Registrar 身份
- **THEN** 顶栏展示当前注册机构名称的标签，无任何下拉或选择交互

#### Scenario: 无身份不展示

- **WHEN** 账号无任何 Registrar 身份
- **THEN** 顶栏不展示该标识
