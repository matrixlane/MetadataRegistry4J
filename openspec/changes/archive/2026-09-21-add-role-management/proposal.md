# Proposal: add-role-management

## Why

侧边栏"基础与核心模块"下的角色（Role）入口自建库以来一直指向占位页：`/roles` 路由注册为 `planned`，前端无列表页，后端也没有任何 `/api/v1/roles` 端点。实体 `Role`（ISO/IEC 11179-3:2023, 6.3.4）与 `RoleRepository` 早已存在，且被 `ContactService`、`RegistrarService` 按 `roleName` 惰性自动创建（如"注册管理员"），但用户无法查看、维护这些角色——角色列表目前留空。需要按 ISO/IEC 11179-3:2023 补全 Role 的管理功能。

## What Changes

- 后端新增 `RoleService` + `RoleController`（`/api/v1/roles`）：分页列表（按 role_name 关键词搜索）、详情、新增、更新、删除、下拉 options。
- `RoleRequest`/`RoleResponse` 覆盖标准表 3 全部属性（role_name、mail_address、email_address 0..\*、phone_number 0..\*），并附带引用计数（contactCount）。
- 删除保护：角色被 Contact（含 Registrar 子类，JOINED 继承同查 contact 表）引用时拒绝删除。
- 应用层约束（标准外）：role_name 必填且全库大小写不敏感唯一——`findByRoleName` 被"注册管理员"等按名查找逻辑依赖，重名会破坏该语义。
- UI：新增 `views/role/RoleList.vue` 列表页（DataTablePage + ResourceFormDialog 既有模式），`/roles` 路由与导航状态 `planned → implemented`。
- 无 schema 变更：`Role` 实体（表 role、role_email、role_phone）已由 `ddl-auto=update` 建表，三方言迁移脚本无需新增。

## Capabilities

### New Capabilities

- `role-management`: ISO/IEC 11179-3:2023 §6.3.4 Role 的 CRUD 契约、引用计数与删除保护、角色列表页。

### Modified Capabilities

（无既有能力的需求语义变更；Contact/Registrar 的按名自动建角色逻辑保持不变。）

## Impact

- `metadata-registry-web`：新增 `dto/role/*`、`RoleService`、`RoleController`、`RoleServiceTest`；`RoleRepository`/`ContactRepository` 增派生查询方法。
- `metadata-registry-ui`：新增 `views/role/RoleList.vue`；改 `router/index.ts`、`api/resources.ts`。
- `README.md`：页面清单去掉"角色（规划中）"标注。
- 标准依据：ISO/IEC 11179-3:2023 §6.3.4（Role 类与表 3 属性）、§6.3.5（Contact.role 引用与约束）。
