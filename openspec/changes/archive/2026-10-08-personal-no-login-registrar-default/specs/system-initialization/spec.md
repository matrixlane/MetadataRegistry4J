# System Initialization Delta Specification

## MODIFIED Requirements

### Requirement: 提交语义

`commit` MUST 按部署版本差异化处理管理员密码：`TEAM` 版 MUST 校验管理员处于待设置密码状态、两次密码输入一致且长度合规（8-64 字符），随后持久化管理员密码哈希、写入 `system.edition` 并将 `system.initialized` 置为 `true`，且 SHALL 签发管理员会话令牌；`PERSONAL` 版 MUST NOT 要求或接受密码输入、MUST NOT 写密码哈希、MUST NOT 签发任何令牌，仅写入 `system.edition` 并将 `system.initialized` 置为 `true`（个人版无登录概念，密码状态保持待设置占位）。两种版本的提交响应 MUST 区分是否携带会话令牌。提交后 `abort` MUST NOT 再清除业务数据。系统不存在注册表运行模式（SINGLE/MULTI）设置，初始化请求与状态响应 MUST NOT 包含 `runningMode` 字段。

#### Scenario: 成功提交

- **WHEN** 管理员在 `TEAM` 版完成 setup（响应携带强制改密向导会话）后提交合法的新密码
- **THEN** 系统持久化密码哈希并完成初始化，后续登录须使用新密码

#### Scenario: 个人版成功提交

- **WHEN** 管理员在 `PERSONAL` 版完成 setup（响应无会话数据）后提交（无密码字段）
- **THEN** 系统完成初始化，应用立即可无登录使用

#### Scenario: 个人版提交携带密码被拒绝或忽略

- **WHEN** 调用方向 `PERSONAL` 版初始化提交中传入密码字段
- **THEN** 系统 MUST NOT 持久化任何密码哈希，按无密码提交处理

#### Scenario: 提交后调用放弃

- **WHEN** 系统已完成初始化，调用方请求 `abort`
- **THEN** 系统 SHALL 视为无操作，业务数据不受影响

### Requirement: 个人版自动成为 Registrar

初始化向导 SHALL 按版本差异化处理"超级管理员是否成为 Registrar"：`PERSONAL` 版 SHALL NOT 向管理员提供该选择——当前超级管理员 SHALL 自动成为所创建注册机构的 Registrar（后端强制 `selfAsRegistrar=true`，前端不展示开关并提示"自动"）；仅 `TEAM` 版 SHALL 提供该选择的开关，由管理员自行决定。`PERSONAL` 版创建的该 Registrar 是管理员的首个 Registrar 身份时，系统 SHALL 自动将其标记为默认注册人员（标志存于 registrar 卫星表，见 `active-registrar-ownership` 能力）。

#### Scenario: 个人版无需选择自动成为

- **WHEN** 管理员在初始化向导选择 `PERSONAL` 并进入注册机构步骤
- **THEN** 向导不展示"设为注册管理员"开关，以"自动"标识说明当前超级管理员将自动兼任 Registrar；提交后管理员即持有 Registrar 身份且被标记为默认注册人员

#### Scenario: 团队版提供选择开关

- **WHEN** 管理员在初始化向导选择 `TEAM` 并进入注册机构步骤
- **THEN** 向导展示"将当前超级管理员设为该机构的注册管理员"开关，管理员勾选则提交后成为 Registrar（首个 Registrar 自动默认），不勾选则不创建
