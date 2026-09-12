# database-portability Specification

## Purpose
TBD - created by archiving change edition-aware-init-and-db-portability. Update Purpose after archive.

## Requirements

### Requirement: 数据库方言支持矩阵
系统 SHALL 将 SQLite、SQL Server、达梦 DM8 作为第一类受支持数据库，达梦与 SQL Server 地位同等。支持范围按部署版本约束：`PERSONAL` 版 MUST 支持 SQLite（默认）并允许配置为 SQL Server 或达梦；`TEAM` 版 MUST NOT 支持 SQLite，仅支持 SQL Server 与达梦等独立数据库。系统在任一适用方言上 MUST 能完成启动、初始化向导、核心业务读写与恢复出厂。

#### Scenario: 个人版默认使用 SQLite
- **WHEN** 应用以 `personal` profile 启动且用户未配置其他数据库
- **THEN** 系统 SHALL 使用 SQLite 文件库运行，无需任何外部数据库服务

#### Scenario: 团队版使用达梦
- **WHEN** 部署版本为 `TEAM` 且数据源配置指向达梦 DM8
- **THEN** 系统 SHALL 使用达梦方言正常工作，行为与 SQL Server 上一致

#### Scenario: 团队版拒绝 SQLite
- **WHEN** 部署版本为 `TEAM`，用户在初始化或系统设置中尝试将数据源配置为 SQLite
- **THEN** 系统 SHALL 拒绝该配置并提示团队版不支持 SQLite

### Requirement: 实体 schema 解耦
实体类 MUST NOT 通过注解硬编码数据库 schema。schema 归属 MUST 仅通过部署配置（如 `hibernate.default_schema`）按数据源指定；未配置时 MUST 使用数据库默认 schema。

#### Scenario: SQLite 下建表
- **WHEN** 应用运行于 SQLite 且未配置 schema
- **THEN** 所有表 SHALL 建在 SQLite 主库中，不引用任何 schema 前缀

#### Scenario: SQL Server 保持 mdr schema
- **WHEN** 应用运行于 SQL Server 且配置 `default_schema=mdr`
- **THEN** 表解析结果 SHALL 与既有部署一致（`mdr` schema 下）

#### Scenario: 达梦使用专用模式
- **WHEN** 应用运行于达梦且配置 `default_schema=MDR`
- **THEN** 所有表 SHALL 建在达梦 `MDR` 模式下

### Requirement: 迁移脚本三轨管理
Flyway 迁移脚本 MUST 按数据库方言分目录管理（`db/migration/sqlserver`、`db/migration/sqlite`、`db/migration/dm`），运行时按当前数据源方言加载对应目录。自本变更起，新增迁移 MUST 同时提供三方言实现，或仅使用三种方言均可执行的可移植 SQL。Hibernate DDL 可表达的结构变更 SHOULD 优先交由 `ddl-auto` 管理，迁移脚本仅承载索引、约束与数据迁移。

#### Scenario: 新增功能迁移
- **WHEN** 开发者提交一个需要新索引的迁移
- **THEN** 该提交 SHALL 在 `sqlserver`、`sqlite`、`dm` 三个目录中各包含一个等价脚本

#### Scenario: 达梦迁移目录不含异方言语法
- **WHEN** 在达梦上执行 `db/migration/dm` 下全部迁移脚本
- **THEN** 所有脚本 SHALL 执行成功，不得包含 `UNIQUEIDENTIFIER`、`DATETIME2`、SQL Server `sys.*` 目录查询或 T-SQL 游标

### Requirement: 业务代码方言中立
业务代码（服务、仓储接口）MUST NOT 包含特定数据库方言的 SQL。确需 native SQL 的查询 MUST 收敛到按方言命名的实现类中，通过方言中立的接口暴露。

#### Scenario: native 查询的方言隔离
- **WHEN** 代码评审发现服务层或通用仓储接口中出现 native SQL
- **THEN** 该查询 SHALL 被移入方言特定实现类后方可合入

### Requirement: 数据清理方言可移植
初始化向导与恢复出厂的数据清理 MUST 通过 JPA 元模型发现业务表，MUST NOT 使用 `INFORMATION_SCHEMA`、`sys.*` 目录查询、`DBCC`、`NOCHECK CONSTRAINT` 等方言专有语句直接拼接执行。外键约束的临时停用 MUST 封装在方言策略接口后（SQLite：`PRAGMA foreign_keys`；SQL Server：约束控制语句；达梦：`ALTER TABLE ... DISABLE CONSTRAINT`）。

#### Scenario: SQLite 上执行初始化放弃
- **WHEN** 系统运行于 SQLite，管理员在初始化向导中执行放弃操作
- **THEN** 系统 SHALL 清空全部业务表且保留管理员账号与系统设置表，不执行任何 T-SQL 专有语句

#### Scenario: 达梦上执行恢复出厂
- **WHEN** 系统运行于达梦，管理员执行恢复出厂设置
- **THEN** 清理结果 SHALL 与 SQL Server 上一致

### Requirement: 达梦第一类支持
达梦 DM8 的接入 MUST 完整落地：引入达梦 JDBC 驱动与方言（优先官方 Hibernate 方言，备选 Oracle 兼容方言并记录差异）、提供 `db/migration/dm` 全套基线迁移脚本、完成类型映射验证（UUID、`TEXT/CLOB`、时间类型、布尔类型），并通过与 SQL Server 相同的验证清单（启动、初始化、核心读写、恢复出厂）。

#### Scenario: 达梦全链路验证
- **WHEN** 应用配置指向达梦 DM8 实例
- **THEN** 系统 SHALL 完成启动建表、初始化向导、数据元素等核心对象读写与恢复出厂，且无方言相关错误

### Requirement: MDR/EXT schema 物理隔离
在 SQL Server 与达梦(team profile)上,标准模型表 SHALL 归 `mdr` schema,扩展表(23 张:ext 模块 10、account、mapping_scheme、mapping_subscription、fhir_element_mapping、interop_mapping、item_link、merge_request、merge_request_item、namespace_permission、registry_node、sync_watermark、system_setting、version_history)SHALL 归 `ext` schema。EXT schema 归属 MUST 仅通过部署配置(team profile 的 JPA mapping-resources,即 orm-team.xml)指定,实体注解 MUST NOT 硬编码 schema。SQLite(local/personal/测试)MUST NOT 使用 schema,所有表留主库,行为与拆分前一致。item_mapping/item_mapping_subject/item_mapping_object 三表 SHALL 归 `mdr`(标准第 11 章内容,用户 2026-09-01 决策)。

#### Scenario: SQLite 路径零变化
- **WHEN** 应用以 local/personal/测试 profile 启动
- **THEN** 不加载 orm-team.xml,全部表解析到 SQLite 主库,启动与读写行为与拆分前一致

#### Scenario: team profile EXT 表归 ext schema
- **WHEN** 应用以 team profile 启动(SQL Server)
- **THEN** 23 张 EXT 表的 ORM 解析结果为 `ext.<table>`,ddl-auto 不会在 `mdr` schema 重复建这些表

#### Scenario: 存量库迁移幂等
- **GIVEN** SQL Server 存量库的 23 张扩展表位于 `mdr` schema
- **WHEN** 执行迁移 V20260901014
- **THEN** 这些表经 `ALTER SCHEMA ext TRANSFER` 迁入 `ext` schema,约束/索引/数据无损;重复执行不报错、不重复迁移

#### Scenario: 原生 SQL 访问 EXT 表
- **WHEN** team profile 下执行引用 EXT 表的原生 SQL(JdbcTemplate/nativeQuery)
- **THEN** SQL 使用显式 `ext.` 前缀,查询正常返回(不依赖连接默认 schema)
