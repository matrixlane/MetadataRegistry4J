## Context

当前系统中，用户账号 `AppUser` 与核心领域模型中的“个人” `Individual` 实体之间没有任何关联。为了支持用户设置个人资料，需要将 `AppUser` 与 `Individual` 关联起来，并在进行数据查询时，根据用户的禁用状态（`enabled = false`）对“个人”列表进行正确的过滤，且保证未关联账户的独立个人数据正常显示。

## Goals / Non-Goals

**Goals:**
- 在 `AppUser` 实体中增加与 `Individual` 实体的关联字段（使用 `@OneToOne`）。
- 编写数据库迁移脚本，在 `app_user` 表中添加 `individual_id` 字段并建立外键。
- 新增当前登录用户获取和更新自己个人资料的 API 端点。
- 修改 `IndividualService.list` 过滤逻辑，排除关联了已禁用用户的个人信息。
- 修改前端菜单以新增“我的”菜单，且在前端“个人”列表中实现角色权限控制（只有 `REGISTRAR` 及以上角色有权新增、修改和删除独立个人）。
- 在 Spring Security 中对当前登录用户更新自己个人信息的端点放宽权限限制，对任何已登录用户开放。

**Non-Goals:**
- 为“独立个人”建立模拟的虚拟用户账号。
- 修改 `Individual` 核心类的核心数据字段。

## Decisions

### 1. 实体与表结构关联设计
在 `AppUser` 实体中添加与 `Individual` 实体的单向一对一关联。
- **配置方式**：
  ```java
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "individual_id")
  private Individual individual;
  ```
- **数据库表更改**（Flyway 迁移）：
  ```sql
  ALTER TABLE app_user ADD individual_id UNIQUEIDENTIFIER NULL;
  ALTER TABLE app_user ADD CONSTRAINT fk_app_user_individual FOREIGN KEY (individual_id) REFERENCES individual(id);
  ```

### 2. 个人资料管理 API 端点
在 `AuthController` 中新增两个端点：
- `GET /api/v1/auth/me/individual`：获取当前登录用户的 Individual 个人信息。如果当前用户还没有关联 Individual，则返回 200，但数据对象为 null/空。
- `PUT /api/v1/auth/me/individual`：更新当前登录用户的个人信息。如果不存在则自动创建一个新的 `Individual` 实例并将其关联到当前 `AppUser`；若存在则直接更新。
- **安全拦截规则**：在 `SecurityConfig` 中，将上述端点排在通用角色限制规则的前面，并仅要求 `.authenticated()`，允许任何角色（如 VIEWER）的已登录用户访问。

### 3. "个人" 列表的禁用用户过滤
在 `IndividualService` 查询列表时，通过 JPA Criteria 构建子查询：
- 排除所有存在 `AppUser` 关联且该 `AppUser.enabled = false` 的 `Individual` 实例。
- SQL 逻辑相当于：
  ```sql
  SELECT i.* FROM individual i 
  WHERE NOT EXISTS (
      SELECT 1 FROM app_user u 
      WHERE u.individual_id = i.id AND u.enabled = 0
  )
  ```

### 4. 前端菜单与路由调整
- 菜单：在 `AppLayout.vue` 菜单底部新增“我的”项，点击跳转到 `/my-profile`。
- 权限：在 `IndividualList.vue` 页面中，将 `DataTablePage` 的 `:can-create`、`:can-edit`、`:can-delete` 属性绑定到 `auth.isRegistrar`，从而仅允许 `REGISTRAR` 和 `ADMIN` 用户管理独立个人。

## Risks / Trade-offs

- **[Risk]** 禁用用户与删除用户的级联关系。如果 AppUser 被删除，其对应的 Individual 是否应该被级联删除？
  - **Mitigation**：由于 Independent Individual 可以没有关联用户，因此 AppUser 删除时可以根据业务选择不删除其 `Individual`，仅将外键置空以转换成“独立个人”；但如果需要严格级联，在 `deleteUser` 时也必须注意同步解除或删除。按题意“已禁用的用户如果存在个人信息，则此个人信息不会继续在‘个人’界面中显示”，因此在禁用时直接通过 status 状态控制即可。
- **[Risk]** 超级管理员在未设置个人信息前在“个人”界面不显示。
  - **Mitigation**：符合要求“默认，没有新增任何Individual实例，需要通过进入‘我的’来更新个人信息”。
