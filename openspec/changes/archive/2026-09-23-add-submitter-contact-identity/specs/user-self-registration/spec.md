# user-self-registration 增量（add-submitter-contact-identity）

## ADDED Requirements

### Requirement: 注册与登录响应暴露联系人完善提示标志

系统 SHALL 在登录响应与"我的"信息接口中暴露 `hasContact`、`hasRegistrar` 与 `profilePrompt` 标志（`profilePrompt` = 非超级管理员、无 Registrar 身份且未关联任何 Contact），供前端判定是否向该账号展示"完善联系人信息"提示。标志 MUST 由服务端依据账号当前绑定状态计算，MUST NOT 依赖客户端推断。

#### Scenario: 新注册未登记联系人

- **WHEN** 新注册且未关联 Contact 的普通用户登录
- **THEN** 响应中 `hasContact=false`、`hasRegistrar=false`、`profilePrompt=true`

#### Scenario: 初始化成为 Registrar 的 Admin

- **WHEN** 初始化向导中选择成为 Registrar 的 Admin 登录
- **THEN** 响应中 `hasRegistrar=true`、`profilePrompt=false`

#### Scenario: 已登记联系人

- **WHEN** 已绑定至少一个 Contact 的用户登录
- **THEN** 响应中 `hasContact=true`、`profilePrompt=false`
