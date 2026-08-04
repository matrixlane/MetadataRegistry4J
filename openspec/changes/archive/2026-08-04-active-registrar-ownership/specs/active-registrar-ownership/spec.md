## ADDED Requirements

### Requirement: 活动 Registrar 硬约束

账号进行注册工作（注册受管项、生成或编辑 `Scoped_Identifier`、编辑标识/版本生成策略）前，系统 MUST 要求该账号已激活一个且仅一个 Registrar 身份。账号仅有一个 Registrar 身份时 SHALL 自动激活；拥有多个 Registrar 身份时 MUST 显式选择其一后方可继续；不具有任何 Registrar 身份的账号（含 SUPER_ADMIN）MUST NOT 执行注册工作。

#### Scenario: 单一身份自动激活

- **WHEN** 仅拥有一个 Registrar 身份的账号登录系统
- **THEN** 系统自动激活该身份，账号可直接进行注册工作

#### Scenario: 多身份必须显式选择

- **WHEN** 拥有两个以上 Registrar 身份的账号登录后尝试进入注册相关功能
- **THEN** 系统要求其先选择激活一个 Registrar 身份，未选择前阻止注册操作并展示选择引导

#### Scenario: 无 Registrar 身份禁止注册

- **WHEN** 不具有任何 Registrar 身份的账号（含 SUPER_ADMIN）尝试注册受管项或生成标识
- **THEN** 系统拒绝该操作并提示需要 Registrar 身份

### Requirement: Registrar 身份切换

系统 SHALL 提供活动 Registrar 身份切换入口，展示当前身份（注册机构与 Registrar 名称）。切换后 SHALL 使旧会话上下文失效并以新身份重建；其后的注册操作 MUST 以新激活身份记账。

#### Scenario: 切换身份生效

- **WHEN** 多身份账号在顶栏切换器中选择另一个 Registrar 身份
- **THEN** 活动身份立即更新，后续注册操作以新身份的注册机构归属记账

#### Scenario: 身份展示

- **WHEN** 账号已激活 Registrar 身份
- **THEN** 界面顶栏展示当前注册机构与 Registrar 名称

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
