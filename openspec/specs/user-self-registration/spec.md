# User Self Registration Specification

## Purpose
This capability provides self-registration options on the login screen and exposes administrative settings to control new user self-registration audit workflows.

## Requirements

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

### Requirement: MUST 新用户审核开关设置与状态控制
MUST 超级管理员可以在系统设置中控制“新用户注册是否需要审核才允许登录”的开关。开关开启或关闭时，新注册账户的初始启用状态与超级管理员可见的审核菜单会有所不同。

#### Scenario: 开启注册审核
- **WHEN** 超级管理员在系统设置中开启“新用户是否需要审核才允许登录”开关，且有新用户进行注册
- **THEN** 新注册用户账户的启用状态（`enabled`）默认为未启用（`false`）
- **AND** 超级管理员可在用户管理页面中查看新注册待审核的用户并进行审核启用
- **AND** 前端用户管理页面显示待审核列表和审核操作按钮

#### Scenario: 关闭注册审核
- **WHEN** 超级管理员在系统设置中关闭”新用户是否需要审核才允许登录”开关，且有新用户进行注册
- **THEN** 新注册用户账户的启用状态（`enabled`）直接设为启用（`true`）
- **AND** 超级管理员在用户管理中不可见”审核新注册用户”的功能/待审核列表

### Requirement: MUST 限制前台新增用户的系统角色
MUST 在后台或前台用户管理中创建或编辑账户时，系统角色选项必须只能选择”普通用户”，禁止新增或变更为”超级管理员”角色。整个系统只允许初始化时创建的单个超级管理员账号拥有该角色。

#### Scenario: 管理员在用户管理中新增用户
- **WHEN** 超级管理员打开用户管理并点击”新建”用户
- **THEN** 系统角色的下拉选项中仅提供”普通用户”选项
- **AND** 无法选择或变更为”超级管理员”

### Requirement: MUST 用户管理列表增加上次登录时间
MUST 用户管理列表必须直观展示用户的最近一次登录记录，新增”上次登录时间”列。

#### Scenario: 查看用户列表
- **WHEN** 管理员打开用户管理页面
- **THEN** 表格中包含”上次登录时间”列，并以可读日期格式（如 `YYYY-MM-DD HH:mm:ss`）展示各账号最近登录时间，若从未登录则展示 `-`

### Requirement: MUST 允许自主注册的系统开关与校验
MUST 系统必须提供”允许注册新用户”的控制开关（默认开启），在关闭该开关时，访客将无法从前台访问注册接口，且前端登录页面的”注册”按钮应被隐藏。

#### Scenario: 关闭自主注册功能
- **WHEN** 超级管理员在系统设置中关闭”允许注册新用户”开关
- **THEN** 登录页面隐藏”注册”入口按钮
- **AND** 任何调用注册接口 `/api/v1/auth/register` 的请求都会被拒绝，并返回提示”系统已关闭自主注册功能”

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
