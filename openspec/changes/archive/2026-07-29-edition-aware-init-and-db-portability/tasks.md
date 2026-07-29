## 1. 实体 schema 解耦（D3）

- [x] 1.1 移除全部 30 个实体类的 `@Table(schema = "mdr")` 硬编码，仅保留表名
- [x] 1.2 在 `application.yml` 中将 `hibernate.default_schema` 改为按数据源配置（SQL Server 默认保留 `mdr`，SQLite 不设置，达梦默认 `MDR`）
- [x] 1.3 验证 SQL Server 上启动后表解析位置不变（`mdr` schema），既有测试套件通过

## 2. 迁移脚本三轨化（D4）

- [x] 2.1 将 `db/migration/` 存量 20 个脚本迁移至 `db/migration/sqlserver/`，`spring.flyway.locations` 按方言指向对应目录
- [x] 2.2 编写 SQLite 等价基线脚本集 `db/migration/sqlite/`（UUID→TEXT、去除 sys 目录查询/游标/过滤索引 T-SQL 语法）
- [x] 2.3 编写达梦等价基线脚本集 `db/migration/dm/`（UUID→VARCHAR(36)、DATETIME2→TIMESTAMP、sys 查询→达梦数据字典、游标→达梦 PL/SQL 或应用侧逻辑）
- [x] 2.4 在贡献指南或 AGENTS.md 中补充"新增迁移必须三方言或可移植"的规则

## 3. 数据清理方言化（D5、D6）

- [x] 3.1 新增 `DatabaseCleaner` 组件：基于 JPA Metamodel 发现业务表（排除 `account`、`system_setting`、`flyway_schema_history` 等保留表）
- [x] 3.2 定义外键控制的方言策略接口，实现 SQLite（`PRAGMA foreign_keys`）、SQL Server（约束控制）、达梦（`DISABLE CONSTRAINT`）三个策略，附依赖序重试删除兜底
- [x] 3.3 改造 `InitWizardService.clearBusinessDataOnly()` 与 `FactoryResetService` 使用 `DatabaseCleaner`，删除 T-SQL 直写
- [x] 3.4 将 `ItemMappingRepository` 递归 CTE 迁移为方言特定仓储实现（sqlserver/sqlite/dm 三份），接口保持方言中立

## 4. SQLite 第一类支持（D2）

- [x] 4.1 引入 `hibernate-community-dialects` 与 `sqlite-jdbc` 依赖
- [x] 4.2 新增 `application-personal.yml`（SQLite 文件库默认路径、连接池 size=1、WAL 模式）与 `application-team.yml`（现有 SQL Server 配置迁入）
- [x] 4.3 处理 SQLite 下的类型映射问题（UUID、LocalDateTime、枚举、TEXT 大字段），跑通应用启动与核心 CRUD
- [x] 4.4 搭建双库验证：同一测试套件在 SQLite 与 SQL Server 上分别通过

## 5. 达梦第一类支持（D10）

> **状态说明**：达梦支持已按第一类标准落地并通过 DM8 实例（Docker `sizx/dm8`）全量验证：启动建表、初始化向导、核心读写、恢复出厂、既有库重启。自研 `DmOracleDialect`（Oracle 兼容 + DM `identity(1,1)` 语法 + BIGINT 类型注册），替代了不兼容 Hibernate 6 的官方方言。

- [x] 5.1 引入达梦 JDBC 驱动与官方 Hibernate 方言依赖，确定方言类名（备选 Oracle 兼容方言，实测后记录结论）
- [x] 5.2 新增达梦数据源 profile 配置（连接池、default_schema=MDR、方言）
- [x] 5.3 处理达梦类型映射问题（UUID→RAW(16)、TEXT→CLOB、时间类型、布尔类型），跑通应用启动与核心 CRUD（需 DM8 实例）
- [x] 5.4 在达梦实例上执行完整验证清单：启动建表、初始化向导、核心读写、恢复出厂；记录官方方言 vs Oracle 兼容方言实测结论

## 6. 初始化向导版本化（D1、D7）

- [x] 6.1 `SystemSettingService` 新增 `system.edition` 读写（PERSONAL/TEAM/CLOUD 枚举，CLOUD 仅占位且提交即拒绝）
- [x] 6.2 `InitWizardSetupRequest` 增加 `edition` 必填字段，`InitWizardStatusResponse` 返回 `edition`
- [x] 6.3 `InitWizardService.setup` 按版本分流：PERSONAL 自动创建默认注册机构/命名空间并设管理员为注册员；TEAM 走现有完整治理流程
- [x] 6.4 `InitWizardService.commit` 持久化 `system.edition` 与治理策略（PERSONAL 强制 SINGLE；TEAM 默认 SINGLE）
- [x] 6.5 系统设置中新增 TEAM 版治理策略切换（SINGLE/MULTI），与 `single-registry-limit-authority` 规则衔接；PERSONAL 不展示该入口
- [x] 6.6 前端 `InitWizard.vue` 实现版本选择步骤与按版本的步骤裁剪；注册机构管理页按版本与治理策略控制新建入口

## 7. 数据源配置（D8）

- [x] 7.1 定义数据源配置模型（vendor、连接参数、schema、连接池默认值），提供连接测试服务（含方言与版本探测，失败不落地）
- [x] 7.2 实现配置持久化：写入 `application-local.yml`，口令不回显，变更后提示重启生效
- [x] 7.3 实现方言合法性校验：TEAM 版拒绝 sqlite，PERSONAL 版三种均接受
- [x] 7.4 初始化向导新增数据库配置步骤（第 2 步）：PERSONAL 可跳过（默认 SQLite）或配置 SQL Server/达梦；TEAM 必须配置且不含 SQLite 选项；配置后引导重启并恢复向导
- [x] 7.5 系统设置新增数据源只读视图与变更入口（仅超级管理员，保存前连接测试 + 重启提示）

## 8. 数据库迁移（D9）

- [x] 8.1 实现只读维护状态开关（迁移期间拒绝写操作）
- [x] 8.2 实现全量导出：按 FK 依赖序逐表导出 JSON Lines（主键原样保留，IDENTITY 表显式 ID）
- [x] 8.3 实现导入：目标库空库校验 → 按序批量写入 → 逐表行数比对
- [x] 8.4 实现切换与回滚：校验通过才写 `application-local.yml`；任一步失败保留原配置与原库，支持中止退出维护状态
- [x] 8.5 系统设置新增迁移向导界面入口（目标方言合法性校验：TEAM 不可迁移到 SQLite）

## 9. 端到端验证

- [x] 9.1 SQLite + personal：全新启动 → 跳过数据库配置 → 精简向导 → 登录 → 核心功能冒烟 → abort/恢复出厂 → 重新初始化
- [x] 9.2 SQL Server + team：配置数据库 → 完整向导 → 治理策略切换 MULTI → 创建第二个注册机构 → 恢复出厂
- [x] 9.3 达梦 + team：配置数据库 → 完整向导 → 核心读写 → 恢复出厂
- [x] 9.4 迁移演练：SQLite 造数据 → 迁移到 SQL Server → 行数校验 → 重启验证；再演练迁移到达梦
- [x] 9.5 存量 SQL Server 数据库升级演练：备份 → 升级启动 → 基线续跑 → 数据完整性校验
- [x] 9.6 前端 e2e（Playwright）覆盖版本选择、数据库配置步骤与两版向导流程
