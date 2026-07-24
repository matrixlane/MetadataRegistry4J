## MODIFIED Requirements

### Requirement: Orderly Database Wipe
执行恢复出厂设置时，系统 MUST 按照外键约束关系或依赖顺序，清空所有的业务数据、用户数据、审计日志、设置项和关联的系统配置。为保证系统可以重新使用，系统 MUST 保留默认的超级管理员账号（admin），将其密码重置为系统默认配置的哈希值（或初始部署值），并将系统初始化状态 `system.initialized` 设为 `false`。

数据清理实现 MUST 在全部受支持数据库方言（SQLite、SQL Server）上可用：业务表的发现 MUST 基于 JPA 元模型而非数据库目录查询；外键约束的临时停用 MUST 封装在方言策略接口之后；代码中 MUST NOT 直接拼接执行 `INFORMATION_SCHEMA`、`sys.*`、`DBCC`、`NOCHECK CONSTRAINT` 等方言专有语句。清理结果（被清空的表集合、被保留的表集合、管理员账号重置、初始化标志复位）在所有方言上 MUST 一致。

#### Scenario: Successful database reset and re-seeding
- **WHEN** a valid factory reset request with the correct password is submitted by the administrator
- **THEN** the system SHALL delete all business items (such as Data Elements, Value Domains, Conceptual Domains, Scoped Identifiers, etc.), delete all users and contacts except the default super admin, reset the default super admin to its initial settings, write `system.initialized = false` into the database, and return a success response.

#### Scenario: SQLite 上执行恢复出厂
- **WHEN** 系统运行于 SQLite，管理员提交合法的恢复出厂请求
- **THEN** 系统 SHALL 完成与 SQL Server 上等价的清理：清空业务数据、保留并重置超级管理员、写入 `system.initialized = false`，全程不执行任何 SQL Server 专有语句
