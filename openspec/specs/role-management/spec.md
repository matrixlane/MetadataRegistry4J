# role-management Specification

## Purpose
补全 Role（ISO/IEC 11179-3:2023, 6.3.4）的端到端管理契约：REST CRUD、标准表 3 属性透传、引用计数与删除保护、角色列表页。

## Requirements

### Requirement: Role CRUD API

系统 SHALL 提供 `/api/v1/roles` 资源端点：分页列表（`q` 按 role_name 忽略大小写包含匹配）、`GET /{id}` 详情、`POST` 新增、`PUT /{id}` 更新、`DELETE /{id}` 删除、`GET /options` 不分页下拉列表（按 roleName 排序）。请求/响应 MUST 覆盖 ISO/IEC 11179-3:2023 表 3 全部属性：`roleName`（role_name 0..1 String）、`mailAddress`（mail_address 0..1 Postal_Address 6.2.9）、`emailAddress`（email_address 0..* String）、`phoneNumber`（phone_number 0..* Phone_Number 6.2.8）。

#### Scenario: 关键词搜索

- **WHEN** 以 `q=管理` 调用 `GET /api/v1/roles`
- **THEN** 结果仅含 role_name 包含"管理"（忽略大小写）的角色

#### Scenario: 多值属性透传

- **WHEN** 创建角色时提交 `emailAddress=["a@x.com","b@x.com"]`、`phoneNumber=["010-1234"]`
- **THEN** 响应与详情原样返回两个邮箱与一个电话

### Requirement: role_name 应用层唯一与必填

管理 API MUST 要求 `roleName` 非空，且全库大小写不敏感唯一；更新时排除自身。该约束为应用层收紧（标准多重性 0..1 保持实体层不变），原因是既有 `findByRoleName` 按名查找/惰性创建逻辑（如"注册管理员"）依赖名称可唯一定位。

#### Scenario: 重名创建被拒

- **WHEN** 已存在 role_name 为"注册管理员"的角色，再创建同名角色（大小写不同亦同）
- **THEN** 返回业务错误，创建失败

### Requirement: 删除保护

删除角色时，若该角色被任一 Contact（含 Registrar 子类，统计 contact 表 role_id 引用）引用，系统 MUST 拒绝删除并返回含引用数的业务错误；未被引用时 MUST 删除成功（连同 role_email/role_phone 明细）。

#### Scenario: 被联系人引用

- **WHEN** 某角色的 contactCount > 0 时调用 DELETE
- **THEN** 返回业务错误"角色正被 N 个联系人引用"，角色保留

#### Scenario: 无引用删除

- **WHEN** 某角色 contactCount = 0 时调用 DELETE
- **THEN** 角色及其邮箱、电话明细被删除

### Requirement: 引用计数展示

`RoleResponse` MUST 携带 `contactCount`（引用该角色的 Contact/Registrar 数）与 `inUse`（contactCount > 0），角色列表页 MUST 展示引用联系人数列。

#### Scenario: 列表显示引用数

- **WHEN** "注册管理员"角色被 2 个 Registrar 引用
- **THEN** 列表该行 contactCount 为 2，inUse 为 true

### Requirement: 角色列表页

前端 `/roles` MUST 提供角色管理页：关键词搜索、分页表格（角色名称/电子邮箱/电话/邮政地址/引用联系人数）、新建/编辑对话框（角色名称必填、邮箱与电话支持多值录入）、删除（后端拒绝时向用户呈现业务错误）。导航注册状态 MUST 从 `planned` 改为 `implemented`。

#### Scenario: 访问角色页

- **WHEN** 已登录用户从"基础与核心模块 → 角色"进入
- **THEN** 显示角色列表页而非占位页，可完成增删改查
