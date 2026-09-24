# System Initialization Capability Specification

## Purpose

本能力定义系统初始化向导在不同部署版本（PERSONAL / TEAM）下的差异化流程、数据库配置步骤、初始化接口契约、幂等与可恢复语义、提交语义以及未初始化状态下的访问控制规则。

## Requirements

### Requirement: 向导流程按版本差异化
初始化向导 SHALL 以版本选择为第一步，数据库配置为第二步（语义见"初始化向导中的数据库配置"）。两个版本 MUST 均包含治理配置步骤（注册机构 → 命名空间 → 注册规范），因为这三者是个人或团队开展所在机构数据治理工作的先决条件。命名空间与注册规范 MUST 作为两个不同的步骤分别设置；管理员角色分配 SHALL 并入注册机构步骤。版本差异仅体现在默认值策略：选择 `PERSONAL` 时，向导 SHALL 为治理字段预填合理默认值以保持精简体验；选择 `TEAM` 时，向导 MUST 要求显式填写必填治理字段。注册机构、命名空间与注册规范在两个版本中 MUST 均为必选：初始化必须创建注册机构关联的自有命名空间；仅导入/访问外部组织数据标准的使用场景 SHALL 通过初始化完成后创建外部命名空间来支持，而非跳过自有命名空间。

#### Scenario: 个人版展示治理步骤并预填默认值
- **WHEN** 管理员选择 `PERSONAL` 并完成数据库步骤
- **THEN** 向导 SHALL 依次展示注册机构、命名空间与注册规范步骤，治理字段预填合理默认值，管理员可直接确认或修改后提交

#### Scenario: 团队版完整向导
- **WHEN** 管理员选择 `TEAM`
- **THEN** 向导 SHALL 要求完成数据库配置，并依次展示注册机构、命名空间与注册表规范配置步骤，全部有效填写后方可进入密码设置步骤

#### Scenario: 任一版本不允许跳过命名空间设置
- **WHEN** 管理员（`PERSONAL` 或 `TEAM`）尝试跳过命名空间步骤并提交
- **THEN** 系统 SHALL 阻止提交并提示自有命名空间为必选项

### Requirement: 初始化向导中的数据库配置
数据库配置 MUST 作为初始化向导的独立步骤，紧随版本选择之后。`PERSONAL` 版 MUST 允许跳过该步骤，跳过时使用默认 SQLite 文件库；`PERSONAL` 版也允许配置 SQL Server 或达梦。`TEAM` 版 MUST 强制完成数据库配置（不得跳过），且 MUST NOT 提供 SQLite 选项。数据库配置的提交、连接测试、持久化与生效方式遵循 `datasource-configuration` 能力的规则。

#### Scenario: 个人版跳过数据库配置
- **WHEN** 管理员在个人版向导的数据库配置步骤选择"使用默认 SQLite"并继续
- **THEN** 向导 SHALL 进入后续步骤，系统继续使用默认 SQLite 文件库

#### Scenario: 团队版必须配置数据库
- **WHEN** 管理员在团队版向导中未完成数据库配置就尝试进入下一步
- **THEN** 向导 SHALL 阻止继续并提示必须先完成数据库配置

#### Scenario: 配置外部数据库后重启继续
- **WHEN** 管理员在向导中配置了 SQL Server 或达梦并通过连接测试
- **THEN** 系统 SHALL 持久化配置并引导重启，重启后向导从目标数据库重新评估状态并允许继续初始化

### Requirement: 初始化请求扩展版本字段
初始化提交接口（`/api/v1/init-wizard/setup`）的请求 SHALL 接受 `edition` 字段（`PERSONAL`/`TEAM`），缺失时 MUST 返回校验错误。初始化状态接口 SHALL 返回当前版本与初始化状态。`PERSONAL` 下治理布尔开关（`selfAsRegistrar`、`createNamespace`、`createRegistrySpecification`）缺省（null）时 SHALL 按 `true` 处理以保持向后兼容；显式 `createNamespace=false` 时 MUST 返回业务校验错误（自有命名空间必选）；显式 `createRegistrySpecification=false` 时 MUST 返回业务校验错误（注册规范必选）。

#### Scenario: 缺少版本字段的提交
- **WHEN** 调用方提交初始化请求但未提供 `edition`
- **THEN** 系统 SHALL 返回参数校验错误，不写入任何业务数据

#### Scenario: 个人版显式关闭命名空间被拒绝
- **WHEN** 调用方提交 `PERSONAL` 初始化请求且 `createNamespace=false`
- **THEN** 系统 SHALL 返回业务校验错误，不写入任何业务数据

#### Scenario: 个人版显式关闭注册规范被拒绝
- **WHEN** 调用方提交 `PERSONAL` 初始化请求且 `createRegistrySpecification=false`
- **THEN** 系统 SHALL 返回业务校验错误，不写入任何业务数据

### Requirement: 幂等与可恢复语义保持
初始化向导 MUST 保持现有幂等与可恢复语义：`setup` 可重复提交（自动清理上次未完成的数据）、`abort` 可安全多次调用、系统未提交（commit）前 MUST 保持未初始化状态。该语义在全部受支持方言上 MUST 一致。

#### Scenario: 重复提交 setup
- **WHEN** 管理员完成一次 `setup` 后未提交，又以不同配置再次调用 `setup`
- **THEN** 系统 SHALL 清理上一次写入的业务数据并以新配置重建，不产生重复注册机构或命名空间

#### Scenario: 放弃后重新开始
- **WHEN** 管理员调用 `abort` 后重新进入向导
- **THEN** 系统 SHALL 处于可重新初始化的干净状态，管理员账号密码重置为待设置状态

### Requirement: 提交语义
`commit` MUST 校验管理员处于待设置密码状态、两次密码输入一致且长度合规（8-64 字符），随后持久化管理员密码哈希、写入 `system.edition`、写入注册表治理策略，并将 `system.initialized` 置为 `true`。提交后 `abort` MUST NOT 再清除业务数据。

#### Scenario: 成功提交
- **WHEN** 管理员完成 setup 后提交合法的新密码
- **THEN** 系统 SHALL 完成初始化，后续登录须使用新密码

#### Scenario: 提交后调用放弃
- **WHEN** 系统已完成初始化，调用方请求 `abort`
- **THEN** 系统 SHALL 视为无操作，业务数据不受影响

### Requirement: 未初始化访问控制保持
系统未初始化时，初始化向导相关端点、数据源连接测试端点与登录端点 MUST 保持匿名可访问；其余 API MUST 要求认证。该行为 MUST NOT 因数据库方言或部署版本不同而改变。

#### Scenario: 未初始化时访问业务接口
- **WHEN** 系统未初始化，未认证客户端请求业务 API
- **THEN** 系统 SHALL 拒绝该请求并返回未认证错误

### Requirement: 注册规范步骤的字段归属
注册规范步骤 MUST 仅包含 `RegistrySpecification` 实体实际持有的设置（名称、默认语言、描述等）。标识生成策略与版本生成策略属于命名空间设置范畴，MUST NOT 出现在注册规范步骤；`identifierPrefix`、`autoGenerateIdentifier`、`identifierPattern`、`versioningPolicy`、`mandatoryClassification` 等无实体承载的字段 MUST NOT 作为注册规范设置采集。

#### Scenario: 注册规范步骤不采集策略字段
- **WHEN** 管理员进入注册规范步骤
- **THEN** 步骤中仅呈现名称、默认语言、描述等注册规范自有设置，不呈现标识生成策略或版本生成策略控件

### Requirement: 向导与全局界面自适应滚动
初始化向导及全局布局 MUST 在内容超过一屏时支持纵向滚动，MUST NOT 出现内容被视口裁切且无法下滚的情况。

#### Scenario: 向导内容超过满屏时可下滚
- **WHEN** 管理员在较小视口（或较多步骤内容）下使用初始化向导，页面内容高度超过视口高度
- **THEN** 页面 SHALL 可纵向滚动，全部步骤内容与操作按钮均可到达

### Requirement: 初始化必须创建注册机构自有命名空间

初始化 MUST 创建注册机构，且 MUST 同时创建与该注册机构关联的自有命名空间（1:1，两边 0..1）；命名空间设置 MUST NOT 可跳过。自有命名空间创建时 SHALL 同步建立标识生成策略（段模板，PUBLISHED 且立即生效）与版本生成策略（自动化）。标识生成策略类型 MUST 收敛为段模板唯一选择：初始化请求携带 `identifierStrategy` 为 `SEGMENT_TEMPLATE` 以外任何值（含 UUID、SEQUENCE、TEMPLATE）MUST 返回业务校验错误；向导界面 MUST NOT 呈现策略类型单选。向导命名空间步骤 MUST 就地内嵌标识模板编辑器，供管理员在初始化时直接编排模板；请求未携带模板 JSON 时 SHALL 落系统默认模板（OBJECT_ABBREVIATION + SEPARATOR(".") + SEQUENCE(pad=5)，示例 `DE.00001`）；标识符输入模式为 MANUAL_ONLY 时编辑器 SHALL 禁用。默认模板、预设与界面文案 MUST NOT 引用任何特定行业标准编号。初始化完成之后创建的命名空间 SHALL 为外部命名空间，使用手动编辑策略。

#### Scenario: 初始化完成即具备自有命名空间与策略

- **WHEN** 管理员完成初始化向导
- **THEN** 系统存在注册机构及其关联的自有命名空间，该命名空间的段模板标识策略（PUBLISHED）与版本自动化策略已生效

#### Scenario: 向导就地编排模板并生效

- **WHEN** 管理员在向导命名空间步骤中将模板编排为 LITERAL("WN") + SEPARATOR(".") + OBJECT_ABBREVIATION + SEPARATOR(".") + SEQUENCE(pad=5) 并完成初始化
- **THEN** 自有命名空间已发布策略即为该模板，首个数据元注册生成 `WN.DE.00001`

#### Scenario: 策略类型白名单收敛

- **WHEN** 初始化请求携带 `identifierStrategy` 为 `UUID`（或 `SEQUENCE`、`TEMPLATE`）
- **THEN** 系统返回业务校验错误，提示标识生成策略仅支持段模板

#### Scenario: 不允许跳过命名空间创建

- **WHEN** 初始化请求显式声明不创建命名空间
- **THEN** 系统返回业务校验错误，提示自有命名空间为必选项

#### Scenario: 初始化后创建的命名空间为外部

- **WHEN** 初始化完成后用户再创建命名空间
- **THEN** 新命名空间的性质为外部，其标识与版本策略为手动编辑

### Requirement: 个人版自动成为 Registrar

初始化向导 SHALL 按版本差异化处理"超级管理员是否成为 Registrar"：`PERSONAL` 版 SHALL NOT 向管理员提供该选择——当前超级管理员 SHALL 自动成为所创建注册机构的 Registrar（后端强制 `selfAsRegistrar=true`，前端不展示开关并提示"自动"）；仅 `TEAM` 版 SHALL 提供该选择的开关，由管理员自行决定。

#### Scenario: 个人版无需选择自动成为

- **WHEN** 管理员在初始化向导选择 `PERSONAL` 并进入注册机构步骤
- **THEN** 向导不展示"设为注册管理员"开关，以"自动"标识说明当前超级管理员将自动兼任 Registrar；提交后管理员即持有 Registrar 身份

#### Scenario: 团队版提供选择开关

- **WHEN** 管理员在初始化向导选择 `TEAM` 并进入注册机构步骤
- **THEN** 向导展示"将当前超级管理员设为该机构的注册管理员"开关，管理员勾选则提交后成为 Registrar，不勾选则不创建

### Requirement: setup 业务数据范围含注册系统默认上下文

初始化向导的业务数据创建范围 SHALL 在注册规范创建完成之后扩展一步：创建注册系统默认上下文（Registry Context）并回填注册规范的默认上下文。该步骤 MUST 晚于注册机构自有命名空间标识/版本生成策略的配置生效，Registry Context 的命名空间 MUST 为注册机构的自有命名空间，其标识符与版本 MUST 由该命名空间已配置的生成规则产出；该步骤 MUST 在系统初始化提交（commit）时以独立事务执行，其失败 MUST NOT 阻断 setup 的其余业务数据创建与 commit 本身。

#### Scenario: 初始化完成后默认上下文就绪

- WHEN 管理员完成初始化向导（setup 提交且 commit 成功）
- THEN 系统中存在归属向导注册机构的 Registry Context（标识/版本来自其自有命名空间的生成规则），且注册规范的默认上下文指向该 Registry Context

#### Scenario: 该步骤失败不阻断向导

- WHEN Registry Context 创建步骤失败而其余治理数据（注册机构、命名空间、注册规范）创建成功
- THEN 初始化照常完成；Registry Context 由后续启动的补建路径再次尝试创建
