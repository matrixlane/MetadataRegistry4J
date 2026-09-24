# Design: add-role-management

## 标准依据

ISO/IEC 11179-3:2023（第四版）：

- **§6.3.4 Role class**："Role is a class each instance of which models a role which an individual can play as a contact within an organization."
- **§6.3.4.2 表 3 — Attributes of Role class**：

| 属性名 | 多重性 | 数据类型 | 定义 |
| --- | --- | --- | --- |
| role_name | 0..1 | String (6.2.11) | name of the position that fulfils the role |
| mail_address | 0..1 | Postal_Address (6.2.9) | postal address for the role |
| email_address | 0..* | String (6.2.11) | email addresses of the role |
| phone_number | 0..* | Phone_Number (6.2.8) | phone numbers for the role |

- **§6.3.5 Contact class**：`role 0..1 Role`；约束 6.3.5.3 要求 Contact 的 individual 或 role 至少有其一。

既有 `Role` 实体（common-facilities，`@StandardReference("ISO/IEC 11179-3:2023, 6.3.4")`）已逐项对齐表 3：`roleName`、`mailAddress`（Embedded PostalAddress）、`emailAddress`（ElementCollection → role_email 表）、`phoneNumber`（ElementCollection → role_phone 表）。本变更不改动实体与表结构。

## 现状与缺口

- 实体与 `RoleRepository`（`findByRoleName`）已存在；`ContactService.batchConfigure`、`RegistrarService` 在需要时按名惰性建 Role。
- 无 Role 的 Service/Controller/DTO/列表页；`/roles` 路由指向 PlaceholderPage，导航注册状态 `planned`。

## 方案

### 后端（metadata-registry-web）

- **DTO**（`web/dto/role/`）：
  - `RoleRequest(String roleName, String mailAddress, List<String> emailAddress, List<String> phoneNumber)`，`roleName` 加 `@NotBlank`。
  - `RoleResponse(UUID id, String roleName, String mailAddress, List<String> emailAddress, List<String> phoneNumber, long contactCount, boolean inUse)`。
  - `RoleOption(UUID id, String roleName)`（下拉框用，对齐 ContextOption 模式）。
- **RoleRepository**：增 `findByRoleNameContainingIgnoreCase(String, Pageable)`、`existsByRoleNameIgnoreCase(String)`、`findByRoleNameIgnoreCase(String)`。
- **ContactRepository**：增 `countByRoleId(UUID)`——Contact 为 JOINED 继承根，Registrar 子类外键同在 contact 表，一次统计即覆盖两者。
- **RoleService**：
  - `list(q, pageable)`：q 空走 `findAll`，否则按 role_name 包含匹配（忽略大小写）。
  - `create/update`：role_name 去空白后做全库大小写不敏感唯一校验（应用层约束，理由见下），update 排除自身。
  - `delete`：`contactRepository.countByRoleId(id) > 0` 时抛 `BusinessException`（HTTP 400，文案含引用数）。
  - `options()`：全量按 roleName 排序，供下拉框。
  - 集合字段做 null/空白过滤归一；`mailAddress` 空白归一为空值。
- **RoleController**（`/api/v1/roles`，Tag "3. 主体 - 角色"，对齐 ContactController 的注解与返回包裹 `ApiResponse`/`PageResponse`）：GET 列表（q + Pageable）、GET `{id}`、GET `options`、POST、PUT `{id}`、DELETE `{id}`。
- 权限：沿用 `SecurityConfig` 对 `/api/v1/**` 的既有规则（写操作 SUPER_ADMIN/REGISTRAR），不新增条目。

### 应用层约束说明（偏离记录）

- role_name 必填 + 唯一：标准多重性为 0..1，本系统因 `findByRoleName` 驱动"注册管理员"等按名查找与惰性创建逻辑而收紧为必填且唯一（忽略大小写）。该约束只作用于管理 API，不改动实体层的标准多重性。

### 前端（metadata-registry-ui）

- `views/role/RoleList.vue`：复用 `DataTablePage` + `ResourceFormDialog` 模式（对齐 DocumentTypeList）。
  - 列：角色名称（sortable）、电子邮箱（逗号连接 0..\*）、电话（逗号连接 0..\*）、邮政地址、引用联系人数（contactCount）。
  - 表单：角色名称（必填）、邮政地址、电子邮箱（多值标签录入）、电话（多值标签录入）。
- `router/index.ts`：`/roles` 指向 RoleList；routeTable 注册 `status: 'implemented'`。
- `api/resources.ts`：`roleApi = crud('/roles')` + `options()`。

### 不做的事

- 不做 Contact 表单的角色下拉改造（可后续用 `/roles/options` 增强）。
- 不新增迁移脚本（无 schema 变更）；不预置种子角色（保持既有惰性创建语义）。

## 风险

- 删除保护避免破坏 Contact/Registrar 的 role 外键引用；无级联删除。
- `contactCount` 逐行统计在当前数据量级（页大小 ≤50）可接受；与既有逐行装配响应的模式一致。
