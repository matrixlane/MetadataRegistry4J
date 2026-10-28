# 变更提案:dbo 遗留表清理(cleanup-dbo-leftover-tables)

## Why

2026-09-01 `mdr-ext-schema-split` 变更把 23+2 张扩展表从 `mdr` 迁入 `ext` 并清理了 17 张无实体
孤儿表，但所有迁移守卫只检查 `TABLE_SCHEMA='mdr'`，**从未检查 `dbo`**。更早的迁移
（2026-05-13 `V20260513003`/`V20260513004`）用未限定表名建表，落在当时的连接默认 schema
`dbo`；`V20260603001` 随后将当时 dbo 下用户表批量迁入 `mdr`，而本批 12 张在此之后又以某种
方式重建/残留于 `dbo`（如个人版某时期未加载 orm-team.xml，Hibernate 按连接默认 schema dbo
自动建表），历次清理均未覆盖，遗留至今。

这违反现行规范：

- `AGENTS.md`「数据模型分层规则（强制）」：`mdr` 只放 ISO/IEC 11179 标准定义内容，
  标准以外一切归 `ext`；`dbo` 不在设计内。
- `openspec/specs/database-portability/spec.md` Requirement「MDR/EXT schema 物理隔离」。

本机 SQL Server 库（`metadata_registry`）2026-10-28 实测：`dbo` 下 12 张业务表全部 0 行，
外键仅在孤儿 FHIR 集合内部，`ext.interop_mapping`/`ext.version_history` 存在；
本机部署（personal）经 `~/.metadata-registry/application-local.yml` 加载 `orm-team.xml`，
JPA 实际路由到 `ext.*`（启动日志 `alter table metadata_registry.ext.version_history` 佐证），
dbo 副本无任何访问方。详细证据见 `docs/superpowers/specs/2026-10-28-dbo-leftover-cleanup-design.md`。

## What Changes

新增三方言同版本迁移 `V20261028001__Drop_dbo_leftover_tables.sql`，幂等清理三类残留：

1. **无实体孤儿表（8 张，存在即删）**：`fhir_resource`、`fhir_element`、`fhir_value_set`、
   `fhir_code_system`、`fhir_binding`、`data_quality_task`、`notification_preference`、
   `notification_record`（阶段 2 已删功能的残留，代码中无 `@Table` 实体）。
2. **已移除功能的残留（2 张，存在即删）**：`mapping_scheme`、`mapping_subscription`
   （功能已于 V20260920001 从 `ext` 删除，dbo 副本为残留）。
3. **活表的 dbo 死副本（2 张，守卫删除）**：`interop_mapping`、`version_history`——
   仅当 `dbo.T` 存在 **且** `ext.T` 存在 **且** `dbo.T` 行数为 0 时删除；任一不满足则跳过
   （该环境归 DBA 事项，先例：mdr-ext-schema-split design.md 第 119 行）。

分方言策略：

- **SQL Server**：按依赖序守卫 DROP（先 `fhir_binding` → `fhir_element` → 其余）；
  不触碰 `mdr`/`ext` 任何对象。
- **SQLite**：`DROP TABLE IF EXISTS` 上述 10 张非活表（`fhir_binding` 为首次清理）；
  **不得**删 `interop_mapping`/`version_history`（个人版 SQLite 主库下是活表）。
- **达梦**：DM 无 `dbo`，按既有 idiom（PL/SQL 块 + `ALL_TABLES` 守卫 +
  `EXCEPTION WHEN OTHERS THEN NULL`）删除 `MDR`（及防御性检查 `EXT`）owner 下的
  10 张非活表；同样不触碰 `INTEROP_MAPPING`/`VERSION_HISTORY`。

纯增量迁移脚本，不改任何 Java 代码、ORM 映射与既有迁移。

## Capabilities

- `database-portability`：新增 Requirement「SQL Server dbo schema 零业务表」
  （含三方言迁移幂等性与活表保护的场景）。

## Impact

- **受影响模块**：metadata-registry-web（仅新增 3 个迁移脚本）。
- **受影响环境**：所有存量 SQL Server 库（首启执行清理）；SQLite/达梦清理各自方言副本，
  全新空库全守卫 no-op。
- **风险**：某环境若 `dbo.interop_mapping`/`dbo.version_history` 有数据（理论上说明该部署
  personal 模式未加载 orm-team.xml、dbo 为活表），行数守卫跳过删除，保留现场。
- **回退**：被删表均为 0 行死表/死副本，无数据回退需求；结构回退＝从备份恢复或按
  V20260513003 等历史脚本重建。
