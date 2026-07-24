## Context

系统当前只有唯一的超级管理员账户（`admin`），默认在系统启动时通过 `DataInitializer` 进行初始化（原默认密码为 `Admin@123`）。
为了提升安全性和管理便利性：
1. 超级管理员账户首次登录应免密，但必须强制修改密码；
2. 当超级管理员遗忘密码时，提供部署目录下的本地文件应急登录码恢复通道；
3. 限制非管理员用户提升为超级管理员；
4. 增加针对超级管理员账户自身的删除和禁用保护；
5. 提供对系统“允许注册新用户”的控制开关。

## Goals / Non-Goals

**Goals:**
- 系统只允许存在一个超级管理员账户。
- 超级管理员首次登录时，无需密码即可登录，登录后强制要求修改密码。
- 超级管理员可使用部署目录下的 `emergency-code.txt` 文件中的应急码进行登录，应急码每次使用后重置刷新。
- 使用应急登录码登录后，强制修改密码，且新旧密码不可相同。
- 使用拦截器（Interceptor）阻断处于“强制改密”状态下的其他 API 请求。
- **[新增]** 超级管理员不能禁用或删除自己的账号。
- **[新增]** 用户管理列表中新增“上次登录时间”列展示。
- **[新增]** 超级管理员可从系统配置开启或关闭“允许注册新用户”（默认开启），并在关闭时从前台隐藏注册入口并拒接注册接口请求。
- **[新增]** 超级管理员可针对普通用户进行“重设密码”操作，生成只展示一次的随机临时密码，此密码仅可登录一次且登录后强制改密。

**Non-Goals:**
- 不支持多超级管理员账户（整个系统角色中，`SUPER_ADMIN` 有且仅有 `admin` 一个人）。
- 普通用户的首次登录逻辑和密码找回逻辑不受此设计影响。

## Decisions

### 1. 超级管理员密码初始化与免密登录判定
- 在 `DataInitializer.java` 中，将 `admin` 的初始密码哈希值设为 `"NO_PASSWORD"`。
- 在 `AuthService.login` 方法中：
  - 如果登录用户名是 `admin`，且数据库中的 `passwordHash` 值为 `"NO_PASSWORD"`，则**免密通过**验证（登录请求中密码字段可以为空，或输入任意内容均放行）。
  - 登录成功后，在返回的 JWT Token 中携带 `forceChangePassword` 声明，且在其 profile 响应中设置 `forceChangePassword = true`。

### 2. 应急登录码存储与刷新机制
- 应急登录码保存在系统部署目录下的本地文件 `emergency-code.txt` 中。
- **验证登录**：当超级管理员登录时，若输入的密码与 `emergency-code.txt` 里的内容一致，则通过验证：
  - 标记该次登录为“应急登录”。
  - 立即生成一个全新的随机应急登录码（10位字母数字组合）并覆写至 `emergency-code.txt` 中。
  - 在 JWT Token 中携带 `forceChangePassword` 和 `emergencyLogin` 声明，且 in profile 响应中设置 `forceChangePassword = true`。
- **随机应急登录码生成逻辑**：
  - 在系统启动时（例如在 `AuthService` 初始化或 `DataInitializer` 中），如果 `emergency-code.txt` 不存在，自动生成一个初始的随机码写入该文件。

### 3. JWT Claims 与安全拦截器
- 在 `JwtService.java` 中，生成 Token 时支持传入或计算 `forceChangePassword` 状态并写入 Claim：
  - `claims.put("forceChangePassword", true)`
  - `claims.put("emergencyLogin", true)`（仅对应急登录）
- 在 `JwtAuthenticationFilter.java` 中，将这些 claims 解析并传递给 Spring Security Context 中绑定的 `AuthenticatedUser`。
- 增加 `ForcePasswordResetInterceptor.java` 拦截器：
  - 拦截所有以 `/api/v1/` 开头的请求。
  - **白名单**：`/api/v1/auth/login`、`/api/v1/auth/me`（用于获取当前用户信息）、`/api/v1/auth/change-password`。
  - 如果当前登录用户的 `forceChangePassword` 标识为 `true`，且请求路径不在白名单中，拦截并返回 `498`（或 `403` 并在响应体中指示需修改密码）。

### 4. 强制修改密码校验
- 在 `AuthService.changePassword` 方法中：
  - 若用户通过应急登录码登录（通过 Security Context 或 JWT Claim 获取 `emergencyLogin` 状态）：
    - 强制校验新密码与数据库中现有的密码哈希，使用 `passwordEncoder.matches(newPassword, oldPasswordHash)`。若匹配成功，抛出业务异常：“新密码不能与原密码相同”。
  - 修改密码成功后，更新数据库中 `admin` 的密码哈希，且后续登录将只允许使用新密码。

### 5. 前端强制改密交互
- 在前端 Axios 拦截器中，捕获响应 of 自定义错误状态（例如 `498` 状态码，或者登录响应中的 `profile.forceChangePassword == true`）。
- 强制弹出不可关闭的“修改超级管理员密码”或“修改临时密码”弹窗，限制用户在成功修改密码前无法操作 any 其他页面。

### Decisions 6. 超级管理员自身操作安全防护
- **后端拦截**：在 `AccountController.java` 层面，对于更新（`update`）、禁用（`audit`）和删除（`delete`）接口，获取当前认证用户 `AuthenticatedUser`。如果传入的目标操作 `id` 等于当前登录的 `user.id()` 且包含禁用或删除意图，直接阻断并抛出 `BusinessException("不能禁用/删除自己的账号")`。
- **前端禁用**：在 `AppUserList.vue` 的模板中，根据当前 Pinia 存储的登录用户信息 `auth.user.id` 进行比对。为当前登录账户对应行上的“禁用”与“删除”按钮绑定 `:disabled="row.id === auth.user?.id"`，防止误操作。

### Decisions 7. 用户管理列表展示“上次登录时间”
- **后端返回**：在 `AuthService.toProfile` 方法中转换的 `AccountProfileResponse` 已包含 `lastLoginAt`，但需要在前端将其显式展示。
- **前端列配置**：在 `AppUserList.vue` 增加对应列，绑定并格式化 `lastLoginAt` 为可读格式，若为空则显示 `-`。

### Decisions 8. 允许注册新用户全局开关与校验
- **后端数据库与业务逻辑**：
  - 在 `SystemSettingService.java` 中引入配置键 `system.allow_user_registration`，默认值为 `true`。
  - 在 `SystemController.java` 中暴露控制该开关状态的 Get/Put 接口，并仅允许 `SUPER_ADMIN` 调用。
  - 在 `AuthService.register(AccountCreateRequest)` 注册核心方法中，优先校验此开关状态。若关闭，抛出 `BusinessException("系统已关闭自主注册功能")`。
- **前端注册入口逻辑**：
  - 增加一个非登录态可以访问的公共接口 `/api/v1/auth/register/allowed`，返回此开关的 boolean 值，并在 `SecurityConfig.java` 中放行此路径。
  - 在前端登录页 [Login.vue](file:///本仓库工作区/metadata-registry-ui/src/views/login/Login.vue) 挂载时调用此接口。若开关为 `false`，则隐藏“注册账号”的按钮。
  - 在 [SystemSettings.vue](file:///本仓库工作区/metadata-registry-ui/src/views/system/SystemSettings.vue) 页面中增加“允许注册新用户”的控制开关。

### Decisions 9. 普通用户密码重设与单次登录强制改密
- **数据库扩展**：
  - 在 `mdr.account` 表中，新增两个字段：`force_password_reset` (BIT) 和 `temp_password_used` (BIT)。
  - 编写 Flyway SQL 迁移文件，增加这两个字段并将默认值设为 `0` (false)。
- **重置密码 API**：
  - 在 `AccountController` 新增 `POST /api/v1/accounts/{id}/reset-password`，仅限 `SUPER_ADMIN` 权限。
  - 接口逻辑：
    - 验证被重置用户存在，且不能是 `SUPER_ADMIN` 本身。
    - 生成 10 位长度的随机字符串作为临时密码。
    - 使用 `passwordEncoder.encode` 将临时密码加密，更新到账户的 `passwordHash`。
    - 设置 `forcePasswordReset = true` 和 `tempPasswordUsed = false`。
    - 保存并返回临时密码的明文给前端（此密码不会被日志记录，且仅在当前 API 响应中返回一次）。
- **登录校验逻辑扩展**：
  - 在 `AuthService.login` 中校验普通用户时：
    - 如果 `user.isForcePasswordReset() == true`：
      - 若 `user.isTempPasswordUsed() == true`，抛出 `BadCredentialsException("临时密码已失效，请联系管理员重新重置密码")`。
      - 密码比对通过后，将 `user.setTempPasswordUsed(true)`，保存。
      - 在生成的 JWT Claims 中写入 `"forceChangePassword" = true`，使用户进入全局强制改密状态。
- **前端展示与按钮逻辑**：
  - 在 `AppUserList.vue` 用户列表的“操作”列中，针对普通用户增加“重置密码”按钮。
  - 点击按钮发出重置请求，成功后，利用 `ElMessageBox` 弹窗展示该一次性临时密码，提供一键复制功能。

## Risks / Trade-offs

- **[Risk]** 应急登录码被多设备并发读取。
  - **Mitigation**：每次成功匹配后立即重写，并在内存中进行同步保护。
- **[Risk]** 超级管理员在未改密时强行刷新页面以绕过前端限制。
  - **Mitigation**：后端拦截器（`ForcePasswordResetInterceptor`）严格校验 JWT Token 中的 Claim，即使前端被绕过，任何数据 API 请求也会被后端拒绝。
- **[Risk]** 临时密码在使用后被重复利用。
  - **Mitigation**：利用 `temp_password_used` 字段在登录成功瞬间立即设为 true 并持久化，数据库和后端拦截策略确保只有第一次登录请求能通过，彻底切断重用通道。
