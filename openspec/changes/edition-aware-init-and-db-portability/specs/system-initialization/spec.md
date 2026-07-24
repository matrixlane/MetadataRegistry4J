## ADDED Requirements

### Requirement: 向导流程按版本差异化
初始化向导 SHALL 以版本选择为第一步，数据库配置为第二步（语义见"初始化向导中的数据库配置"）。选择 `PERSONAL` 时，向导 MUST 精简（注册机构、命名空间与注册表规范以默认值自动创建，无需填写治理字段）。选择 `TEAM` 时，向导 MUST 提供完整治理配置步骤（注册机构 → 管理员与命名空间 → 注册表规范 → 管理员密码）。

#### Scenario: 个人版精简流程
- **WHEN** 管理员选择 `PERSONAL`，跳过数据库配置（使用默认 SQLite）并设置管理员密码后提交
- **THEN** 系统 SHALL 自动创建默认注册机构与默认命名空间，完成初始化，全程无需填写治理字段

#### Scenario: 团队版完整向导
- **WHEN** 管理员选择 `TEAM`
- **THEN** 向导 SHALL 要求完成数据库配置，并依次展示注册机构、命名空间与注册表规范配置步骤，全部有效填写后方可进入密码设置步骤

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
初始化提交接口（`/api/v1/init-wizard/setup`）的请求 SHALL 接受 `edition` 字段（`PERSONAL`/`TEAM`），缺失时 MUST 返回校验错误。初始化状态接口 SHALL 返回当前版本与初始化状态。

#### Scenario: 缺少版本字段的提交
- **WHEN** 调用方提交初始化请求但未提供 `edition`
- **THEN** 系统 SHALL 返回参数校验错误，不写入任何业务数据

### Requirement: 幂等与可恢复语义保持
初始化向导 MUST 保持现有幂等与可恢复语义：`setup` 可重复提交（自动清理上次未完成的数据）、`abort` 可安全多次调用、系统未提交（commit）前 MUST 保持未初始化状态。该语义在全部受支持方言上 MUST 一致。

#### Scenario: 重复提交 setup
- **WHEN** 管理员完成一次 `setup` 后未提交，又以不同配置再次调用 `setup`
- **THEN** 系统 SHALL 清理上一次写入的业务数据并以新配置重建，不产生重复注册机构或命名空间

#### Scenario: 放弃后重新开始
- **WHEN** 管理员调用 `abort` 后重新进入向导
- **THEN** 系统 SHALL 处于可重新初始化的干净状态，管理员账号密码重置为待设置状态

### Requirement: 提交语义
`commit` MUST 校验管理员处于待设置密码状态、两次密码输入一致且长度合规（6-20 字符），随后持久化管理员密码哈希、写入 `system.edition`、写入注册表治理策略，并将 `system.initialized` 置为 `true`。提交后 `abort` MUST NOT 再清除业务数据。

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
