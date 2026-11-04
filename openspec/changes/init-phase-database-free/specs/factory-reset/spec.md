## MODIFIED Requirements

### Requirement: MUST Orderly Database Wipe
MUST 执行恢复出厂设置时，系统必须按照外键约束关系或依赖顺序，清空所有的业务数据、用户数据、审计日志、设置项和关联的系统配置。为保证系统可以重新使用，系统必须保留默认的超级管理员账号（admin），将其密码重置为系统默认配置的哈希值（或初始部署值），并将系统初始化状态 `system.initialized` 设为 `false`。系统 MUST 同步把本地初始化状态记录重置为未初始化，使恢复出厂后"是否已初始化"的判定不再依赖数据库查询。

#### Scenario: Successful database reset and re-seeding
- **WHEN** a valid factory reset request with the correct password is submitted by the administrator
- **THEN** the system SHALL delete all business items (such as Data Elements, Value Domains, Conceptual Domains, Scoped Identifiers, etc.), delete all users and contacts except the default super admin, reset the default super admin to its initial settings, write `system.initialized = false` into the database, reset the local initialization state record to not-initialized, and return a success response.
