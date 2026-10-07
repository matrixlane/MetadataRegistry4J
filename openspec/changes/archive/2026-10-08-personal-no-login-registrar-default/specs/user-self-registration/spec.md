# User Self Registration Delta Specification

## MODIFIED Requirements

### Requirement: MUST 用户登录页自主注册

MUST 用户登录界面必须提供开放的注册功能，任何访客均可输入用户名和密码进行账户注册。该能力 SHALL 仅适用于 `TEAM` 版：`PERSONAL` 版不存在登录界面与注册入口，自助注册对个人版 MUST NOT 提供任何入口，注册接口在个人版下亦 MUST NOT 产生业务效果以外的用途变更（端点保留但不呈现于个人版界面）。

#### Scenario: 注册新账号

- **WHEN** 访客在登录页面点击“注册”并输入合规的用户名和密码提交
- **THEN** 系统在 `mdr.account` 表中创建一条新的账号记录

#### Scenario: 个人版无注册入口

- **WHEN** `PERSONAL` 版系统被访问
- **THEN** 前端不呈现登录页与"注册"入口；自助注册、用户审核、用户管理入口在个人版界面均不存在

#### Scenario: 关闭自主注册功能

- **WHEN** 超级管理员在系统设置中关闭”允许注册新用户”开关
- **THEN** 登录页面隐藏”注册”入口按钮
- **AND** 任何调用注册接口 `/api/v1/auth/register` 的请求都会被拒绝，并返回提示”系统已关闭自主注册功能”
