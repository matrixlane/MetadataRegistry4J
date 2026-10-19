# 设计：SQLite 空库启动与共同迁移基线

## 1. 目标与边界

保证全新检出、没有本地 SQLite 文件的个人版可以启动、执行静态预置并进入初始化向导；选择新的 SQLite 路径后，重启应用会使用该路径并完成结构初始化。

SQL Server 当前最大迁移版本 `20261009012` 是新库共同基线的权威版本。SQL Server 当前最终实体结构是最终模型的权威，SQLite 与达梦通过各自方言映射实现等价结构。基线版本变化不改写已部署库的 Flyway 历史。

本变更不重构业务实体，不改变 `mdr`/`ext` 的归属规则，不改变初始化业务语义，也不触碰任何用户现有数据库文件。

## 2. 当前故障链

1. 个人版默认 JDBC URL 指向 `./data/metadata-registry.db`，而仓库不保证该文件存在。
2. `application-personal.yml` 将 `ddl-auto` 设为 `none`，空库没有应用表。
3. Flyway 的 `schemas` 为空且 `default-schema` 为空。SQLite 下 Flyway 由此尝试检查空 schema 的 `sqlite_master`，在迁移前失败。
4. 即使强行允许 Flyway 运行，旧 SQLite 迁移 `V20260727001` 使用 SQLite 不支持的 `DROP COLUMN IF EXISTS`，不能从空库重放整条历史链。
5. 数据源服务把用户选择写入 `~/.metadata-registry/application-local.yml`，但 profile 中固定的默认配置优先级更高，重启后路径被覆盖。

此前隔离 SQLite 实测表明：先由 Hibernate 创建当前实体结构，再让 Flyway 在版本 `20261009012` 基线化，可以启动并完成个人版 setup/commit；语义闭环断言通过。这个结果是候选实现的验证证据，不替代合并前对启动顺序与现有库行为的验证。

## 3. 启动和建表策略

### 3.1 空库

- 在应用创建 SQLite 连接前确保所选文件的父目录存在；连接不存在的 SQLite URL 后允许驱动创建数据库文件。
- 对确认为空的新数据库启用 ORM 当前实体结构的建表流程，并确保它先于 Flyway 执行。
- SQLite 的 Hibernate schema 保持空值；Flyway 则使用 `main` 作为默认 schema 和唯一 schema。不得将 `main` 配给 Hibernate 实体，也不得引入 `ext` schema。
- ORM 建表完成后，Flyway 在版本 `20261009012` 建立新库基线，不重放基线之前的旧迁移。静态预置仍在 Flyway 之后、应用就绪前执行。
- `ext` 卫星表遵循既有 SQLite 映射：同名冲突物理表采用 `ext_` 前缀，其他表仍在 SQLite 主库。

空库识别必须发生在任何 DDL 修改之前。不得以“启动时一律 `ddl-auto=update`”代替识别，因为 SQLite 既有库的批量元数据读取会遇到复合 SELECT 限制。

### 3.2 已有数据库

- 已有 Flyway 历史的数据库继续由当前历史和向前迁移驱动；启动不得重建或重新基线化历史表，不得修改既有迁移文件内容/校验和。
- 已有非空 schema 不得被当成空库执行全新建库流程。
- 对存在业务表但没有 Flyway 历史、无法确定来源的库，启动必须避免静默地把它标记为最新基线并跳过必要迁移；实施时需提供明确的兼容处理或失败诊断，不得丢弃数据。

### 3.3 共同版本规则

- 当前共同基线版本为 `20261009012`，取自 SQL Server 当前最终迁移版本。
- 三方言新库的 Flyway 基线记录必须使用该共同版本；后续新迁移必须采用 SQL Server 的版本号，并在 SQLite、SQL Server、达梦提供同版号等价实现。
- 基线之前的 SQL Server/SQLite/达梦脚本清单保留为既有数据库兼容链，不为追求文件数相同而补写无操作脚本，也不删除已被数据库历史引用的脚本。需要折叠的历史差异由共同基线吸收。
- 三方言最终结构的对照以 SQL Server 版本 `20261009012` 的最终模型为准，同时逐项遵守 `AGENTS.md` 的 `mdr`/`ext` 分层约束、SQLite `ext_` 规则和注册项正交链闭合要求。

## 4. 数据源配置优先级

- 个人版 profile 提供的 SQLite URL 仅作为没有用户配置时的默认值。
- 写入 `application-local.yml` 的 URL、驱动、Hibernate 方言/schema 和 Flyway location 必须在重启后对当前 profile 生效。
- 若用户选的是 SQLite，新 URL 的父目录及数据库文件可由连接测试创建；首次应用启动负责补齐 schema 与基线。
- 连接测试失败仍不得保存配置；SQLite 不持久化账号口令。重启提示保持现有行为。

实现可以调整配置导入层级或将持久化值绑定到非冲突的专用配置属性，但最终必须同时兼容 personal/team profile 与环境变量覆盖，并避免配置文件相互引用形成循环占位符。

## 5. 数据模型与标准约束

- 仅 ISO/IEC 11179 标准定义的表/列可归 `mdr`；应用字段继续归 `ext` 卫星表。
- SQLite 没有 schema，所有表仍在 `main`；仅既有同名卫星表使用 `ext_` 物理前缀。
- 注册项的内容链和受管链保持正交；初始化数据必须由既有应用服务建立完整 `item_identification`/`registered_item`/`administered_item` 或 `attached_item` 关联。不得用缺失关联链的裸 SQL 基线替代。

## 6. 验收证据

1. 在临时目录中删除/不存在默认 DB 文件，启动 personal profile；确认 DB 文件、核心表、Flyway 基线和静态预置生成，初始化状态端点可用。
2. 在临时目录选择另一个不存在的 SQLite 路径并保存；重启后确认应用连接的是该路径，文件包含完整 schema 与同版基线。
3. 完成个人版初始化 setup/commit，并通过 SQL 语义断言验证标识条目受管链闭合。
4. 对已有 SQLite 数据库的副本，确认保留原 Flyway 历史与数据；不在仓库内数据库上验证。
5. 静态核对三个迁移目录的当前版本清单、新基线锚点和后续同版规则；不连接真实 SQL Server 或达梦实例。

## 7. 已知限制

本地环境未提供隔离的 SQL Server/达梦实例，因此本变更不能声称完成这两种数据库的运行时建库验收。应在具备临时实例后补做同一版号与 schema 对照。真实 SQL Server 工作库不作为测试目标。
