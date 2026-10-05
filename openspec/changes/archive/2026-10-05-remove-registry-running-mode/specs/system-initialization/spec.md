# System Initialization Delta Specification

## MODIFIED Requirements

### Requirement: 提交语义
`commit` MUST 校验管理员处于待设置密码状态、两次密码输入一致且长度合规（8-64 字符），随后持久化管理员密码哈希、写入 `system.edition`，并将 `system.initialized` 置为 `true`。提交后 `abort` MUST NOT 再清除业务数据。系统不存在注册表运行模式（SINGLE/MULTI）设置，初始化请求与状态响应 MUST NOT 包含 `runningMode` 字段。

#### Scenario: 成功提交
- **WHEN** 管理员完成 setup 后提交合法的新密码
- **THEN** 系统 SHALL 完成初始化，后续登录须使用新密码

#### Scenario: 提交后调用放弃
- **WHEN** 系统已完成初始化，调用方请求 `abort`
- **THEN** 系统 SHALL 视为无操作，业务数据不受影响
