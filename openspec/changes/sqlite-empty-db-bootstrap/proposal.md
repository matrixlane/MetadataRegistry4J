# SQLite 空库启动与共同迁移基线

## Why

刚签出的代码在本地没有 SQLite 数据库文件时，个人版不能稳定完成首次启动和系统初始化。当前个人版将 Hibernate DDL 关闭、Flyway 默认 schema 留空；Flyway 无法在 SQLite 上探测历史表。即使用户在数据源配置中选了新的 SQLite 路径并保存，`application-personal.yml` 的 profile 配置仍会盖过本地保存的 URL，重启后应用继续连接默认路径。

三个迁移目录的版本清单也不一致：SQL Server 有 77 个版本，SQLite 有 55 个（其中 3 个为 Java 迁移），达梦有 57 个；三个目录当前的最大版本号均为 `20261009012`。由于应用尚未发布，可以为新库建立共同基线；SQL Server 当前最终结构和版本序列作为权威。既有数据库的迁移历史仍须保留，避免 Flyway 校验失败或重放迁移。

## What Changes

- 让默认 SQLite 路径及用户选定的 SQLite 路径在文件不存在时都能创建数据库文件，并在首次启动时建立应用所需的最终结构。
- 让持久化的数据源配置在重启后覆盖个人版 profile 的默认路径。
- 为 SQLite 正确配置 Flyway 的 `main` schema，并让静态预置与初始化向导在数据库结构就绪后运行。
- 将 SQL Server 当前最终版本 `20261009012` 作为三个方言新库的共同基线锚点；基线结构以 SQL Server 当前最终状态为准。
- 保留既有数据库已应用的迁移脚本、版本、校验和与历史记录。基线以下的历史清单可以继续因方言兼容性而不同；基线之后新增迁移必须使用 SQL Server 的版本号，并提供三方言等价实现。

## Capabilities

### New Capabilities

- 无。

### Modified Capabilities

- `database-portability`: 定义空数据库建表、三方言共同基线和迁移版本约束。
- `datasource-configuration`: 定义已保存数据源配置重启后优先生效及 SQLite 文件创建行为。
- `system-initialization`: 定义全新 SQLite 库结构、Flyway 与静态预置的启动顺序。

## Impact

- 配置与启动：个人版数据库初始化、Flyway SQLite schema、空库 DDL 与启动先后顺序。
- 数据源配置：本地配置文件优先级和 SQLite 路径持久化。
- 迁移：新库共同基线版本为 SQL Server 当前最大版本 `20261009012`；不改写既有迁移历史或校验和。
- 文档：同步 README 中的首次启动/SQLite 配置说明；若启动流程图有实质变化，再更新 GitNexus 生成的架构文档。
- 风险边界：验证仅使用临时 SQLite 文件；不对真实 SQL Server 库执行迁移或写入。
