# System Initialization Delta Specification

## MODIFIED Requirements

### Requirement: 向导流程按版本差异化
初始化向导 SHALL 以版本选择为第一步，数据库配置为第二步（语义见"初始化向导中的数据库配置"）。两个版本 MUST 均包含治理配置步骤，且步骤顺序为：注册规范 → 注册机构 → 命名空间。管理员角色分配 SHALL 并入注册机构步骤。版本差异仅体现在默认值策略：选择 `PERSONAL` 时，向导 SHALL 为治理字段预填合理默认值以保持精简体验；选择 `TEAM` 时，向导 MUST 要求显式填写必填治理字段。注册规范、注册机构与命名空间在两个版本中 MUST 均为必选：初始化必须先创建注册规范，再创建注册机构与注册人员，最后创建命名空间；初始化必须创建注册机构关联的自有命名空间；仅导入/访问外部组织数据标准的使用场景 SHALL 通过初始化完成后创建外部命名空间来支持，而非跳过自有命名空间。

#### Scenario: 个人版展示治理步骤并预填默认值
- **WHEN** 管理员选择 `PERSONAL` 并完成数据库步骤
- **THEN** 向导 SHALL 依次展示注册规范、注册机构与命名空间步骤，治理字段预填合理默认值，管理员可直接确认或修改后提交

#### Scenario: 团队版完整向导
- **WHEN** 管理员选择 `TEAM`
- **THEN** 向导 SHALL 要求完成数据库配置，并依次展示注册规范、注册机构与命名空间配置步骤，全部有效填写后方可进入密码设置步骤

#### Scenario: 任一版本不允许跳过命名空间设置
- **WHEN** 管理员（`PERSONAL` 或 `TEAM`）尝试跳过命名空间步骤并提交
- **THEN** 系统 SHALL 阻止提交并提示自有命名空间为必选项

## ADDED Requirements

### Requirement: 治理对象创建顺序与注册机构 Primary 标志
初始化 `setup` MUST 在同一事务内按以下顺序创建治理对象：先创建注册规范（Registry_Specification），再创建注册机构（Registration_Authority）与注册人员（Registrar），最后创建自有命名空间。注册规范的创建 MUST NOT 依赖注册机构的存在，且注册规范 MUST 作用于系统全局（每个注册系统一条）。初始化创建的注册机构 MUST 在后台被标记为 Primary（隐式标志，不进入任何响应 DTO 或前端界面）；删除保护语义见 `registration-authority-lifecycle` 能力。初始化完成后由注册表上下文引导创建 Registry Context 并回填注册规范默认上下文的机制保持不变。

#### Scenario: 注册规范先于注册机构创建

- **WHEN** 管理员提交初始化 setup
- **THEN** 系统先于注册机构创建注册规范；注册规范不引用任何注册机构，且创建失败时不产生注册机构数据

#### Scenario: 初始化机构带 Primary 标志

- **WHEN** 管理员完成初始化向导提交
- **THEN** 本次创建的注册机构在后台被标记为 Primary；任何 API 响应与前端界面均不出现该标志
