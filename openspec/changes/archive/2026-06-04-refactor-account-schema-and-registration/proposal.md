## Why

目前系统中同时存在 `app_user` 和 `registry_user` 两个用户表，存在重叠和干扰，用户模型不够清晰。此外，系统缺乏新用户注册与审核机制，且数据库 Schema 命名不统一。为了简化系统架构、强化数据隐私，我们需要将用户账户模型重构为单一的 `account` 表（Schema 更换为 `mdr`），并将真实的个人信息完全隔离到 `individual` 表中。同时，需在登录页提供开放注册功能，并为超级管理员提供“新用户注册审核”的开关和审批功能，并清理多余的测试仿真数据。

## What Changes

- **重构用户表**：移除 `app_user` 和 `registry_user` 表，合并并重构为 `mdr.account` 表。
- **字段精简与表关联**：
  - `account` 表仅保留 `username`、`password_hash`、`sys_role` (系统角色)、`enabled` (启用状态) 以及审计字段 (`id`, `created_at`, `last_login_at` 等)。
  - 移除原 `email` 与 `displayName` 字段（`email` 已存在于 `individual` 表中，显示名称通过关联 `individual` 获取）。
  - `account` 可一对一关联 `0..1` 个 `individual` (通过 `individual_id` 外键或关联属性)。
- **角色体系清晰化**：
  - `account.sys_role` 代表系统角色（如超级管理员 `SUPER_ADMIN`，暂无其它角色）。
  - 业务角色仍由 `role` 表和 `contact.role` 维护。
- **联系人与个人资料机制调整**：
  - 超级管理员在初始化单注册表并成为“注册管理员”后，在个人列表中**不会**自动产生一条 Individual 记录。
  - 此时只会在联系人列表中显示一条 `Registrar` 记录（对应的 `Contact` 记录有注册机构、`role` 为“注册管理员”，但 `Contact.individual` 为空）。
  - 直到该用户在“我的”页面创建个人资料（即 `individual`）后，才会在个人列表中出现对应的记录，并将 `Contact.individual` 关联上。
- **开放注册与审批流程**：
  - 登录页增加“注册账户”入口。
  - 系统设置中增加“新用户是否需要审核才允许登录”开关（超级管理员可控）。
  - **开启审核时**：新注册用户默认为未启用（`enabled = false`），超级管理员在用户管理中可见“审核”功能并可启用账户。
  - **关闭审核时**：新注册用户默认启用（`enabled = true`），用户管理中的审核功能不可见。
- **数据库 Schema 迁移**：将所有表（如 `account`、`individual`、`system_setting` 等）统一更换为 `mdr` Schema（例如 `mdr.account`）。
- **初始化数据清理**：移除所有测试仿真的业务数据，仅保留系统运行所必须的初始化数据（如默认系统设置、内置系统角色、超级管理员账号等）。

## Capabilities

### New Capabilities
- `user-self-registration`: 用户登录页的自主注册以及对应的审核开关与审批流程。

### Modified Capabilities
- `user-profile-individual`: 调整用户账户与 Individual 的一对一关联逻辑，优化“我的”个人资料创建时机，更新菜单展示及列表过滤逻辑。
- `registry-specification`: 统一更换数据库 schema 至 `mdr`，以及在初始化单注册表时，注册管理员联系人 records 的生成逻辑与角色展示。

## Impact

- **数据模型**：实体类 `AppUser` 与 `User` 将合并重构为 `Account`（对应 `mdr.account` 表）。
- **数据库迁移**：Flyway 脚本需要升级，将现有表前缀或 schema 变更为 `mdr`，并执行表结构重组。
- **认证与权限**：Spring Security 配置、JWT 生成与校验逻辑需适配新的 `Account` 实体。
- **业务逻辑**：
  - 注册管理员初始化逻辑需要修改：不自动生成 `individual`，只生成没有关联 `individual` 的 `Contact`。
  - “我的”保存个人资料时，将新建 `individual` 并更新 `account` 以及对应的 `Contact` 关联。
- **前端页面**：
  - 登录页新增注册入口与注册弹窗。
  - 系统设置页面新增审核开关。
  - 用户管理页面新增审核列表/审核按钮（基于开关状态显示）。
  - 联系人/个人列表过滤与展示逻辑适配（展示“注册管理员”角色等）。
