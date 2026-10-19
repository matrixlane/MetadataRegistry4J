# database-portability Delta

## ADDED Requirements

### Requirement: 全新数据库按共同最终模型建表并基线化

系统 SHALL 能在没有 SQLite 数据库文件的全新检出环境中启动个人版，并创建可用于初始化向导的完整数据库结构。空库建表 SHALL 先于 Flyway 和静态预置执行；在 SQLite 上，Hibernate SHALL 不使用 schema，Flyway SHALL 将 `main` 配置为默认且唯一 schema。SQLite 数据表 SHALL 保持在主库，标准表和扩展卫星表继续遵循 `mdr`/`ext` 分层规则及 SQLite `ext_` 物理名前缀规则。

SQL Server 当前最终状态 SHALL 作为共同基线结构的权威。共同新库基线版本 SHALL 为当前 SQL Server 最新版本 `20261009012`。新库 SHALL 记录该共同基线，不得重放基线之前不可从空库执行的历史迁移。

#### Scenario: 无 SQLite 文件的个人版首次启动

- **GIVEN** 默认 SQLite 数据库文件和其父目录中的数据库文件均不存在
- **WHEN** 以 `personal` profile 启动应用
- **THEN** 应用 SHALL 创建数据库文件及完整应用表结构，Flyway SHALL 记录版本 `20261009012` 的基线，静态预置完成后初始化状态端点可用

#### Scenario: 选择新 SQLite 路径后首次启动

- **GIVEN** 用户保存了一个尚不存在的 SQLite 文件路径
- **WHEN** 使用保存后的配置重启应用
- **THEN** 应用 SHALL 在该路径创建数据库文件及完整应用表结构，并记录共同基线 `20261009012`

#### Scenario: 新库初始化后的注册项关联闭环

- **GIVEN** 新 SQLite 库完成个人版初始化
- **WHEN** 检查初始化产生的已标识条目
- **THEN** 每个已标识条目 SHALL 沿 `item_identification` 关联拥有完整注册项受管链，并 SHALL 恰好属于 `Administered_Item` 或 `Attached_Item` 之一

### Requirement: 三方言迁移版本以 SQL Server 为准

SQL Server 当前最终迁移版本 SHALL 作为共同新库基线和未来迁移版本序列的权威。共同基线之后新增的每个迁移版本 MUST 在 SQLite、SQL Server、达梦三个目录中使用相同版本号并实现等价效果。基线之前既有迁移历史可因方言兼容性保留不同脚本清单；系统 MUST NOT 为清单对齐而重写已应用迁移、改变校验和、删除被既有历史引用的脚本或添加未经语义核验的无操作迁移。

已有 Flyway 历史的数据库 MUST 保留版本、状态、校验和与历史行，不得被新库基线重新基线化，也不得重放已应用迁移。

#### Scenario: 新库以 SQL Server 最新版本建立共同基线

- **GIVEN** 任一受支持方言的空数据库
- **WHEN** 应用完成结构初始化
- **THEN** Flyway 历史 SHALL 以 SQL Server 当前最新版本 `20261009012` 作为共同基线

#### Scenario: 后续新增迁移对齐版本

- **WHEN** 新增一个数据库迁移版本
- **THEN** 三个方言目录 SHALL 提供相同版本号的迁移，并实现等价的结构/数据语义

#### Scenario: 既有历史保持不变

- **GIVEN** 数据库已存在 Flyway 历史
- **WHEN** 使用新版本应用启动
- **THEN** 系统 SHALL 保留原有历史与校验和，并仅按适用顺序执行未应用的后续迁移
