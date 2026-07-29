## Context

目前 Metadata Registry 部署完成后，系统无引导界面，默认仅有一个超级管理员账户。需要设计并配置一个向导式流程，指引管理员完成系统运行模式设置、默认注册机构（RA）与注册管理员（Registrar）的关联、命名空间（Namespace）、组织与联系人（Contact）及其角色的批量初始化，以及基于命名空间的细粒度权限配置。

系统中的 `AppUser` 用于 Web 层的认证与授权，而 `User` 是 ISO 11179 的领域实体。因此在做初始化配置时，需要建立 `AppUser`、`User`、`Contact`、`Registrar` 之间的桥梁关系，实现业务逻辑上的一致性。

## Goals / Non-Goals

### Goals:
- 提供前后台一套向导式的初始化配置机制。
- 引入 `SystemSetting` 实体持久化系统模式与初始化状态，若未初始化则强制拦截非向导 API 并重定向。
- 扩展 `Contact` 关联 standard `User`，允许任何联系人绑定到注册的系统用户。
- 建立基于 `NamespacePermission` 的命名空间访问控制列表（ACL）设计，实现用户与组织权限的继承与覆盖策略。
- 保证命名空间被删除时，若有关联的 `Scoped_Identifier` 则抛出警示或予以警告。
- 支持批量添加联系人并设置组织与角色。
- 实现外部命名空间支持（可无注册机构，其命名机构可关联外部 Organization，支持自动新建或选择已有组织）。

### Non-Goals:
- 本次不实现多注册表模式的实际运行逻辑（UI 层面点击多注册表模式不可用/置灰，后端预留字段与接口但不可配置）。

## Decisions

### 1. 系统配置项存储与强制初始化拦截
**Decision**: 引入 `SystemSetting` 键值对表来存储配置，而不是新增包含固定字段的单行实体。
- **Rationale**: 键值对设计更容易在未来扩展（如未来多注册表相关配置、SMTP 配置、通知首选项等）。
- **Schema**:
  - `system_setting` 表:
    - `setting_key` VARCHAR(128) PRIMARY KEY
    - `setting_value` VARCHAR(1024)
- **Keys**:
  - `system.running_mode`: `"SINGLE"` (默认) / `"MULTI"` (置灰不可选)
  - `system.initialized`: `"true"` / `"false"`
- **拦截逻辑**: 编写 Spring Boot Interceptor `InitializationWizardInterceptor`。当请求非 `/api/v1/init-wizard/**` 及 `/api/v1/auth/login` 等白名单 API，且 `system.initialized` 不为 `"true"` 时，拦截并返回 `403` / `499` 状态码，促使前端重定向至向导页面。

### 2. 联系人与系统用户关联
**Decision**: 将 standard `User` 关联关系从 `Registrar` 上提到父类 `Contact`。
- **Rationale**: 允许注册管理员将已注册用户设置为各种类型的 `Contact`（Steward、Submitter 等）并授予所属组织。通过使 `Contact` 直接持有 `User` 引用，使普通联系人也能跟系统登录账号挂钩，实现权限校验。
- ** Jpa Entity & Table Changes**:
  - 在 `Contact.java` 中新增:
    ```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    ```
  - 从 `Registrar.java` 中移除冗余的 `user` 字段（`Registrar` 直接通过继承获得 `Contact` 中的 `user`）。
  - 在数据库中，对 `contact` 表增加 `user_id` 列。

### 3. 基于命名空间的细粒度权限控制设计
**Decision**: 新增 `NamespacePermission` 实体，并在业务层使用 AOP 或 Spring Security 表达式进行细粒度校验。
- **Schema**:
  - `namespace_permission` 表:
    - `id` UUID PRIMARY KEY
    - `namespace_id` UUID (FK to `namespace`, 可空，表示全局/未设定)
    - `app_user_id` UUID (FK to `app_user`, 可空，用于用户级权限)
    - `organization_id` UUID (FK to `organization`, 可空，用于组织级权限)
    - `permission_type` VARCHAR(20) (`READ_ONLY`, `READ_WRITE`, `NONE`)
- **权限判定算法** (在 `NamespacePermissionEvaluator` 中实现):
  1. 获取当前登录 `AppUser`。
  2. 若 `AppUser` 角色为 `ADMIN`，默认拥有所有命名空间的 `READ_WRITE` 权限。
  3. 查询该用户在目标 Namespace `N` 上的显式 `NamespacePermission`：
     - 若存在，直接按其 `permission_type` 判定（`READ_WRITE` 允许写，`READ_ONLY` 仅允许读，`NONE` 拒绝所有访问）。
  4. 若用户无显式权限设置，获取用户关联的所有 `Contact` 记录对应的 `Organization` 列表：
     - 查询这些组织在目标 Namespace `N` 上的权限。
     - 若存在组织权限配置，则取最大权限（或首个配置权限，若有冲突则写权限优先）。
     - 若组织存在但未设置任何权限，则默认拥有 `READ_ONLY`（只读）访问权限。
  5. 若用户不属于任何组织（即不是联系人），且用户无显式权限设置，则默认拥有 `READ_ONLY`（只读）访问权限。
- **控制点**: 
  - 所有 Namespace 下的元数据项（DataElement 等）在创建/更新/删除时，均需通过 `NamespacePermissionEvaluator` 校验写权限。

### 4. 命名空间删除级联警示
**Decision**: 在 `NamespaceService.delete` 前，检查是否有关联的 `ScopedIdentifier`。
- **Rationale**: 直接物理删除或级联删除可能导致数据不一致。必须在删除前给用户明确警告。
- **Implementation**:
  - 提供 `GET /api/v1/namespaces/{id}/deletion-check` 接口，返回是否有关联的 `ScopedIdentifier` 数量及明细。
  - 前端在点击删除时，先调用该接口，若数量 > 0 则弹出警报对话框，用户确认后再执行 `DELETE /api/v1/namespaces/{id}`。

### 5. API 接口定义
- **初始化向导**:
  - `GET /api/v1/init-wizard/status`: 获取系统初始化状态和当前运行模式。
  - `POST /api/v1/init-wizard/setup`: 超级管理员提交初始化配置（模式、RA 信息、是否自封为 Registrar 等）。
- **外部命名空间**:
  - `POST /api/v1/namespaces/external`: 创建外部命名空间，参数包含 `namingAuthorityOrganizationId` 或 `newOrganizationName`。
- **联系人批量配置**:
  - `POST /api/v1/contacts/batch`: 批量导入/添加联系人，设置组织及角色。
- **命名空间权限**:
  - `GET /api/v1/namespaces/{id}/permissions`: 获取该命名空间的所有权限配置。
  - `PUT /api/v1/namespaces/{id}/permissions`: 更新该命名空间的组织/用户权限。

## Risks / Trade-offs

- [Risk] 系统未初始化拦截器导致生产环境原有 API 失败 → [Mitigation] 拦截器默认仅在 `system.initialized != "true"` 时起作用，一旦初始化完成，拦截器开销极小且不拦截任何请求。同时提供在 `application.yml` 中一键关闭该拦截器的开关。
- [Risk] 用户级权限与组织级权限冲突 → [Mitigation] 制定明确的优先级规则：用户专属权限 > 继承组织权限 > 默认只读权限。并在权限设置 UI 上给予明确的覆盖关系提示。
