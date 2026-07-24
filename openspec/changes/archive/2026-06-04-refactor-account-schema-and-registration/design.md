## Context

在目前的系统中，`AppUser`（对应 `app_user` 表）和 `User`（对应 `registry_user` 表）存在职责重叠和冗余。我们需要简化用户模型，将用户账户统一到单一的 `Account` 实体中（对应数据库表 `mdr.account`）。
此外，系统目前的所有表没有统一在 `mdr` Schema 下，也缺乏开放注册与审核机制。本项目设计文档将对账户表的统一、数据库 Schema 切换为 `mdr`、新用户注册审核机制、以及系统初始化时的联系人行为进行详细设计。

## Goals / Non-Goals

**Goals:**
- 将 `AppUser` 和 `User` 合并为 `Account`（对应 `mdr.account` 表），并精简其字段（仅包含用户名、密码、`sys_role`、`enabled`、审计字段等），去除 `email` 与 `displayName`（已在 `individual` 表存在）。
- 切换所有数据库表至 `mdr` Schema。
- 实现登录页开放注册功能，且支持管理员控制“注册是否需要审核”的系统设置。
- 调整系统初始化时的逻辑：创建单注册表时成为注册管理员，但不会自动生成 `Individual` 个人记录，仅产生 `individual` 为空的 `Contact` 记录。在“我的”页面首次保存个人信息时，才创建 `Individual` 并与当前账户和 `Contact` 绑定。
- 清理所有非必要的仿真测试数据，仅保留运行所必需的初始化数据。

**Non-Goals:**
- 不涉及复杂的业务多角色（如 Stewardship、Submitter 等）系统角色的复杂化，系统角色仅保留超级管理员 `SUPER_ADMIN`。
- 不修改原有的 ISO 11179 核心领域模型中非用户相关的字段结构。

## Decisions

### 1. 数据模型重构与 Schema 切换
- **实体合并**：删除 `io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.User`，重构 `AppUser` 并重命名为 `Account`（位于 `io.github.matrixlane.metadataregistry.web.auth` 包下）。
- **Schema 统一**：
  - 在所有 JPA 实体类上，使用 `@Table(name = "...", schema = "mdr")`。
  - 编写 Flyway 迁移脚本，将所有的数据库表和序列统一迁移到 `mdr` 模式下，重命名 `app_user` 为 `account`，并删除 `registry_user` 表。
- **Account 属性简化**：
  - `id`: UUID
  - `username`: String
  - `passwordHash`: String
  - `sysRole`: Enum (`SUPER_ADMIN` / `null` 或 `USER` 等，用以代表系统角色)
  - `enabled`: Boolean
  - `individual`: OneToOne 关联 `Individual` (可空)
  - 审计字段：`createdAt`, `lastLoginAt` 等。

### 2. 注册管理员与联系人初始化逻辑调整
- **单注册表初始化**：
  - 在 `InitWizardService` 中，当创建注册机构并将超级管理员绑定为注册管理员时，不再调用自动生成 `Individual` 的方法。
  - 直接创建一个 `Contact` 记录，关联对应的注册机构（`Organization`），设置 `role` 业务角色为“注册管理员”，但 `Contact.individual` 字段保留为 `null`。
- **个人资料创建关联**：
  - 用户登录后，在“我的”页面首次保存个人资料时，系统将创建一个新的 `Individual` 实例，并与 `Account` 建立关联。
  - 同时，系统自动查询该账户关联的 `Contact` 记录（无 `individual` 关联的 `Contact` 记录），并将其 `individual` 属性更新为新创建的 `Individual`。

### 3. 自主注册与审核流程
- **系统设置**：
  - 新增设置项 `new_user_require_audit`。
  - 若为 `true`，注册账户的 `enabled` 设为 `false`；若为 `false`，则直接设为 `true`。
- **接口开发**：
  - `POST /api/auth/register` 允许访客提交 `username` 和 `password`。
  - `PUT /api/accounts/{id}/audit` 允许超级管理员启用/禁用该账号。
- **前端改造**：
  - 登录页增加注册选项与表单。
  - 系统设置中加入开关组件。
  - 用户管理列表支持显示待审核账户，并提供审核按钮。

## Risks / Trade-offs

- **[Risk] Schema 切换导致现有数据库迁移冲突**
  - *Mitigation*: Flyway 迁移脚本应配置为支持在 MS SQL Server 中创建 `mdr` 架构，如果架构不存在则进行创建，并将之前的所有表重新定位到该架构下。
- **[Risk] 旧测试数据清理影响现有的集成测试**
  - *Mitigation*: 更新测试用例与测试环境的数据准备，确保在只有基础系统初始化数据的情况下，测试仍能正常运行。
