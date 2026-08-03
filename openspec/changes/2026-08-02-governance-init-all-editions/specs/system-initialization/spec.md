## MODIFIED Requirements

### Requirement: 向导流程按版本差异化
初始化向导 SHALL 以版本选择为第一步，数据库配置为第二步（语义见"初始化向导中的数据库配置"）。两个版本 MUST 均包含治理配置步骤（注册机构 → 命名空间 → 注册规范），因为这三者是个人或团队开展所在机构数据治理工作的先决条件。命名空间与注册规范 MUST 作为两个不同的步骤分别设置；管理员角色分配 SHALL 并入注册机构步骤。版本差异仅体现在默认值策略：选择 `PERSONAL` 时，向导 SHALL 为治理字段预填合理默认值以保持精简体验；选择 `TEAM` 时，向导 MUST 要求显式填写必填治理字段。注册机构与注册规范在两个版本中 MUST 为必选；命名空间步骤在两个版本中 MUST 允许显式跳过（面向仅导入/访问外部组织数据标准的场景）。

#### Scenario: 个人版展示治理步骤并预填默认值
- **WHEN** 管理员选择 `PERSONAL` 并完成数据库步骤
- **THEN** 向导 SHALL 依次展示注册机构、命名空间与注册规范步骤，治理字段预填合理默认值，管理员可直接确认或修改后提交

#### Scenario: 团队版完整向导
- **WHEN** 管理员选择 `TEAM`
- **THEN** 向导 SHALL 要求完成数据库配置，并依次展示注册机构、命名空间与注册表规范配置步骤，全部有效填写后方可进入密码设置步骤

#### Scenario: 任一版本跳过命名空间设置
- **WHEN** 管理员（`PERSONAL` 或 `TEAM`）在命名空间步骤选择跳过，并完成其余必选步骤后提交
- **THEN** 系统 SHALL 创建注册机构与注册规范、不创建与注册机构关联的命名空间，正常完成初始化

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

### Requirement: 初始化请求扩展版本字段
初始化提交接口（`/api/v1/init-wizard/setup`）的请求 SHALL 接受 `edition` 字段（`PERSONAL`/`TEAM`），缺失时 MUST 返回校验错误。初始化状态接口 SHALL 返回当前版本与初始化状态。`PERSONAL` 下治理布尔开关（`selfAsRegistrar`、`createNamespace`、`createRegistrySpecification`）缺省（null）时 SHALL 按 `true` 处理以保持向后兼容；显式 `createNamespace=false` 时 MUST 跳过命名空间创建；显式 `createRegistrySpecification=false` 时 MUST 返回业务校验错误（注册规范必选）。

#### Scenario: 缺少版本字段的提交
- **WHEN** 调用方提交初始化请求但未提供 `edition`
- **THEN** 系统 SHALL 返回参数校验错误，不写入任何业务数据

#### Scenario: 个人版显式跳过命名空间
- **WHEN** 调用方提交 `PERSONAL` 初始化请求且 `createNamespace=false`
- **THEN** 系统 SHALL 创建注册机构与注册规范，不创建与注册机构关联的命名空间

#### Scenario: 个人版显式关闭注册规范被拒绝
- **WHEN** 调用方提交 `PERSONAL` 初始化请求且 `createRegistrySpecification=false`
- **THEN** 系统 SHALL 返回业务校验错误，不写入任何业务数据
