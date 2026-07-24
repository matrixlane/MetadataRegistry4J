## MODIFIED Requirements

### Requirement: 系统菜单项可见性控制
系统菜单底部必须显示“我的”选项，且该选项面向所有登录用户开放；同时，系统必须隐藏“系统”菜单，仅对系统角色为超级管理员（SUPER_ADMIN）的账户可见。

#### Scenario: 普通用户登录时的菜单展示
- **WHEN** 普通用户（非超级管理员）登录系统并加载菜单
- **THEN** 菜单底部显示“我的”选项，但“系统”菜单被隐藏，不可见

#### Scenario: 超级管理员登录时的菜单展示
- **WHEN** 超级管理员（sys_role 为 SUPER_ADMIN）登录系统并加载菜单
- **THEN** 菜单底部显示“我的”选项，且“系统”菜单也可见

### Requirement: 个人信息设置与 Individual 自动关联
系统必须支持当前登录账户通过“我的”界面设置和更新个人资料。若当前账户尚未关联任何 Individual 实例，在首次保存时系统必须自动创建并关联一个新的 Individual 实例；若已关联，则直接更新已有的 Individual 实例。对于超级管理员成为注册管理员后产生的无 Individual 的 Contact 记录，首次在“我的”页面创建个人资料时，系统必须将新创建的 Individual 与该 Contact 进行关联。

#### Scenario: 首次保存个人信息创建 Individual 实例
- **WHEN** 登录账户且未关联 Individual 实例时，在“我的”页面中提交更新个人信息
- **THEN** 系统为该账户创建一个全新的 Individual 实例，并与该账户进行一对一关联，且在“个人”界面中能看到此实例

#### Scenario: 再次更新个人信息修改已有 Individual 实例
- **WHEN** 已存在关联 Individual 实例的登录账户在“我的”页面更新个人信息并保存
- **THEN** 系统更新该账户已关联的 Individual 实例的属性，不会创建新的 Individual 实例

### Requirement: “个人”界面列表过滤逻辑
在“个人”界面列出所有 Individual 实例时，系统必须排除所有已禁用的账户（`enabled = false`）所关联的 Individual 实例。非禁用账户关联的 Individual 实例以及无关联账户的独立个人实例必须正常显示。

#### Scenario: 禁用用户的个人信息在列表中隐藏
- **WHEN** 超级管理员在账户管理中禁用（将 `enabled` 设置为 `false`）某个账户，并请求获取“个人”界面列表
- **THEN** 系统返回的“个人”界面列表中，不包含该已禁用账户所关联的 Individual 实例

#### Scenario: 启用用户和独立个人的信息在列表中显示
- **WHEN** 用户请求获取“个人”界面列表
- **THEN** 系统返回的“个人”界面列表中，包含所有状态为启用（`enabled = true`）的账户所关联的 Individual 实例，以及所有未关联任何账户的独立个人实例
