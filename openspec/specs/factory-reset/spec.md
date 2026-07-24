## Purpose

TBD

## Requirements

### Requirement: Admin Authentication and Password Validation
为了防范越权和误操作风险，“恢复出厂设置”功能必须强制进行严格的身份验证与密码校验。系统必须验证调用者拥有超级管理员（ADMIN）角色，并且必须验证所输入的管理员当前密码是否正确。

#### Scenario: Non-admin user attempts factory reset
- **WHEN** a user without the ADMIN role calls the factory reset endpoint
- **THEN** the system SHALL reject the request and return a 403 Forbidden status code.

#### Scenario: Admin user submits incorrect password
- **WHEN** an authenticated administrator calls the factory reset endpoint but provides an incorrect current password
- **THEN** the system SHALL reject the request and return a 400 Bad Request status code indicating password verification failure.

### Requirement: Orderly Database Wipe
执行恢复出厂设置时，系统必须按照外键约束关系或依赖顺序，清空所有的业务数据、用户数据、审计日志、设置项和关联的系统配置。为保证系统可以重新使用，系统必须保留默认的超级管理员账号（admin），将其密码重置为系统默认配置的哈希值（或初始部署值），并将系统初始化状态 `system.initialized` 设为 `false`。

#### Scenario: Successful database reset and re-seeding
- **WHEN** a valid factory reset request with the correct password is submitted by the administrator
- **THEN** the system SHALL delete all business items (such as Data Elements, Value Domains, Conceptual Domains, Scoped Identifiers, etc.), delete all users and contacts except the default super admin, reset the default super admin to its initial settings, write `system.initialized = false` into the database, and return a success response.

### Requirement: Security Session Invalidation
在数据库擦除完成后，之前签发的所有 JWT 令牌或活跃的会话必须失效。系统不得允许任何旧会话继续访问系统资源，所有的后续 API 请求（除了允许未初始化的引导/登录接口）都应该被重定向或拒绝。

#### Scenario: Access rejection and redirect post-reset
- **WHEN** a factory reset completes successfully and the admin attempts subsequent API requests
- **THEN** the system SHALL reject the requests with a 401 Unauthorized or 499 Uninitialized code, and redirect the client to the `/init-wizard` view.

### Requirement: Frontend UI Control and Double Confirmation
前端控制台必须在系统设置模块中提供“恢复出厂设置”的操作入口。由于该操作是不可逆的毁灭性数据清除，UI 必须展示醒目的红色危险警告，强制管理员输入当前密码进行二次确认，并在执行中展示全屏加载状态以防网络中断或重复提交。

#### Scenario: UI validation and execution flow
- **WHEN** the admin clicks the "Factory Reset" button, reads the warning, fills in the correct password, and clicks "Confirm" in the modal
- **THEN** the frontend UI SHALL display a loading overlay, trigger the backend reset API, clear Pinia/localStorage user authentication state, show a completion message, and automatically redirect the browser to the initialization wizard route.