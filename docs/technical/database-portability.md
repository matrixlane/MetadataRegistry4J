# 数据库可移植性规则

系统同时支持 SQLite / SQL Server / 达梦 三种数据库方言，遵循以下可移植性规则：

- 表结构由 Hibernate `ddl-auto` 依据实体元数据管理；Flyway 迁移脚本仅承载索引、约束与数据迁移等 Hibernate 无法表达的内容。
- 迁移脚本按方言分目录：`db/migration/sqlserver/`、`db/migration/sqlite/`、`db/migration/dm/`，由 `DB_VENDOR` 环境变量（默认 `sqlserver`）选择加载目录。
- 新增迁移必须可移植（三种方言均可执行），或在每个方言目录各提供一份等价脚本。
- JPA 可表达的索引/约束一律写在实体注解（`@Table(indexes/uniqueConstraints)`、`@Check`），不写进迁移脚本。
- 业务代码禁止方言专有 SQL（`INFORMATION_SCHEMA`、`sys.*`、`DBCC`、`NOCHECK` 等）；确需 native SQL 时放入按方言命名的实现类，接口保持方言中立。
- 实体类不得硬编码 `@Table(schema = ...)`；schema 归属由 `DB_DEFAULT_SCHEMA` 配置决定。
