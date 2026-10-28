# dbo 遗留表清理设计（cleanup-dbo-leftover-tables）

## 1. 背景与问题

2026-09-01 `mdr-ext-schema-split` 变更把 23+2 张扩展表从 `mdr` 迁入 `ext`，并删除了
17 张无实体孤儿表——但所有迁移脚本的守卫都写成 `TABLE_SCHEMA='mdr'`，**从未检查 `dbo`**。
更早（2026-05-13，`V20260513003`/`V20260513004`）一批迁移用未限定表名建表，当时连接默认
schema 为 `dbo`，表全部落进 `dbo`。`V20260603001` 只把账号相关表从 `dbo` 迁入 `mdr`，
其余遗留至今。

违反的不变量（现行规范）：

- `AGENTS.md`「数据模型分层规则（强制）」：`mdr` 只放 ISO/IEC 11179 标准定义内容，
  标准以外的一切统一归 `ext`；`dbo` 不在设计内。
- `openspec/specs/database-portability/spec.md` Requirement「MDR/EXT schema 物理隔离」。

本库（localhost `metadata_registry`，2026-10-28 sqlcmd 实测）：

- `dbo` 下恰好 12 张业务表：`data_quality_task`、`fhir_binding`、`fhir_code_system`、
  `fhir_element`、`fhir_resource`、`fhir_value_set`、`interop_mapping`、`mapping_scheme`、
  `mapping_subscription`、`notification_preference`、`notification_record`、`version_history`，
  **全部 0 行**，无其他 dbo 表。
- 外键仅存在于孤儿 FHIR 集合内部（`fhir_binding`→`fhir_element`/`fhir_value_set`，
  `fhir_element`→`fhir_resource`），无任何外部表引用这 12 张。
- `ext.interop_mapping`、`ext.version_history` 存在（0 行）。
- 活表归属：本机部署（personal）的 `~/.metadata-registry/application-local.yml` 显式设置
  `spring.jpa.mapping-resources: orm/orm-team.xml`，JPA 把 `interop_mapping`/`version_history`
  路由到 `ext.*`；启动日志含 `alter table metadata_registry.ext.version_history` 佐证。
  → `dbo` 副本在任何部署形态下均不被访问（team 走 orm-team.xml；personal+SQL Server 因
  本地配置同样加载 orm-team.xml；SQLite 无 schema 概念）。

## 2. 处置分类

| 类别 | 表 | 处置 |
|---|---|---|
| 无实体孤儿表（阶段 2 已删功能残留） | `fhir_resource`、`fhir_element`、`fhir_value_set`、`fhir_code_system`、`fhir_binding`、`data_quality_task`、`notification_preference`、`notification_record` | 存在即删，幂等守卫 |
| 已移除功能的残留 | `mapping_scheme`、`mapping_subscription` | 功能已于 V20260920001 从 `ext` 删除（用户决策 2026-09-20），dbo 副本存在即删 |
| 活表的 dbo 死副本 | `interop_mapping`、`version_history` | 守卫 DROP：`dbo.T` 存在 **且** `ext.T` 存在 **且** `dbo.T` 行数为 0 才删；任一不满足则跳过（该环境归 DBA 事项，先例：mdr-ext-schema-split design.md 第 119 行） |

## 3. 方案对比

- **方案 A（推荐）**：新增三方言同版本迁移 `V20261028001`，幂等清理。
  最小闭环，纯增量脚本，不改任何 Java 代码与现有迁移。
- 方案 B：A + 防呆守卫（如启动校验"dbo 不得出现业务表"或测试断言）。
  防呆价值真实但属另一关注点，建议另立变更，避免本变更范围膨胀。
- 方案 C：只输出 DBA 手工 SQL 不进迁移。与"schema 由 Flyway 迁移管理"
  （application.yml 基线化 + 幂等守卫链）的现行方向相悖，且无法覆盖 SQLite/达梦副本。

## 4. 迁移设计（V20261028001）

三方言目录同版本号：`db/migration/{sqlserver,sqlite,dm}/V20261028001__Drop_dbo_leftover_tables.sql`。

### 4.1 SQL Server

- 逐表 `IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' ...) DROP TABLE dbo.T;`
- DROP 顺序先子后父：`fhir_binding` → `fhir_element` → `fhir_resource`/`fhir_value_set`/`fhir_code_system` → 其余 7 张（外键仅在集合内部，顺序即足够，无需 FK-cursor）。
- `interop_mapping`/`version_history` 附加双重复核守卫（ext 副本存在 + dbo 0 行）。
- 不触碰 `mdr`/`ext` 下的任何对象。

### 4.2 SQLite

- 无 schema 概念，一律 `DROP TABLE IF EXISTS <name>;`。
- 仅清 10 张非活表（8 孤儿 + `mapping_scheme`/`mapping_subscription`；`fhir_binding` 为首次清理）。
- **`interop_mapping`、`version_history` 不得删除**——个人版（SQLite 主库）下它们是活表。

### 4.3 达梦

- DM 无 `dbo`；迁移脚本一律全限定 `MDR.`/`EXT.`，按既有 idiom 用
  `DECLARE ... ALL_TABLES WHERE OWNER='MDR' ... EXCEPTION WHEN OTHERS THEN NULL` 逐表守卫，
  删除 `MDR`（及防御性检查 `EXT`）owner 下的 10 张非活表。
- 同样不触碰 `INTEROP_MAPPING`/`VERSION_HISTORY`。

## 5. 验收

1. 迁移在本机 SQL Server 库执行后：`SELECT ... FROM sys.tables JOIN sys.schemas ... WHERE s.name='dbo'` 返回 0 业务表。
2. 应用重启正常；注册项列表、版本历史、映射管理页面冒烟通过（走 `ext.*`）。
3. 三方言脚本各自可重复执行（幂等，二次运行为全守卫 no-op）。
4. 构建通过：`metadata-registry-web` 测试（含 `OrmTeamXmlTest` 等既有守卫）不回归。

## 6. 风险与缓解

- **某环境 dbo 副本有数据**：interop/version_history 的行数守卫会跳过删除（保留现场，
  视为 DBA 事项）；孤儿表与 mapping_* 无实体承载，数据无应用含义，存在即删。
- **全新空库**：脚本全部守卫 no-op，不影响 `sqlite-empty-db-bootstrap` 流程。
- **个人版 SQLite 误删活表**：SQLite 脚本白名单式枚举 10 张表，不含
  `interop_mapping`/`version_history`，从清单上排除该风险。
