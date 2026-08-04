## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: 初始化必须创建注册机构自有命名空间
初始化 MUST 创建注册机构，且 MUST 同时创建与该注册机构关联的自有命名空间（1:1，两边 0..1）；命名空间设置 MUST NOT 可跳过。自有命名空间创建时 SHALL 同步建立标识生成策略（段模板，PUBLISHED 且立即生效）与版本生成策略（自动化）。初始化完成之后创建的命名空间 SHALL 为外部命名空间，使用手动编辑策略。

#### Scenario: 初始化完成即具备自有命名空间与策略
- **WHEN** 管理员完成初始化向导
- **THEN** 系统存在注册机构及其关联的自有命名空间，该命名空间的段模板标识策略（PUBLISHED）与版本自动化策略已生效

#### Scenario: 不允许跳过命名空间创建
- **WHEN** 初始化请求显式声明不创建命名空间
- **THEN** 系统返回业务校验错误，提示自有命名空间为必选项

#### Scenario: 初始化后创建的命名空间为外部
- **WHEN** 初始化完成后用户再创建命名空间
- **THEN** 新命名空间的性质为外部，其标识与版本策略为手动编辑
