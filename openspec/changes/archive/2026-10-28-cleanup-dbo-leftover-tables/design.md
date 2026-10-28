# 设计:dbo 遗留表清理(cleanup-dbo-leftover-tables)

## Context

背景与动机见 `proposal.md - Why`；实测证据与设计推导见
`docs/superpowers/specs/2026-10-28-dbo-leftover-cleanup-design.md`。约束要点：

- 现行迁移链既有先例：孤儿表清理 `V20260901015`（守卫 `TABLE_SCHEMA='mdr'`）、
  功能移除 `V20260920001`（先 FK-cursor 清外键再 DROP、`ext` 守卫）。
- Flyway `default-schema=ext`（team）；个人版 SQLite `ddl-auto: none`，schema 由 Flyway 基线管理。
- 三方言脚本必须同版本号；DM 脚本全限定 `MDR.`/`EXT.`。
- `fhir_*` 孤儿表之间存在外键（`fhir_binding`→`fhir_element`/`fhir_value_set`、
  `fhir_element`→`fhir_resource`），其余 9 张无任何外键关联。

## Goals / Non-Goals

**Goals:**

- G1 新增 `V20261028001__Drop_dbo_leftover_tables.sql`（sqlserver/sqlite/dm 三方言），
  使任何存量库首启后 `dbo`（SQL Server）不再承载业务表。
- G2 脚本严格幂等：二次执行全守卫 no-op；全新空库零影响。
- G3 个人版活表（SQLite 主库 `interop_mapping`/`version_history`）零风险：
  从脚本清单上即不可触及。

**Non-Goals:**

- 不新增"防止未来表落 dbo"的启动校验/测试守卫（另立变更）。
- 不改 Java 代码、ORM 映射、既有迁移脚本。
- 不处理 `HTE_*`/`HT_*` 类历史遗留（先例：DBA 事项）。

## Decisions

- **D1 一张迁移覆盖三方言同版本**，而非按方言拆版本。先例：`V20260901014`/`V20260901015`/
  `V20260920001` 均三方言同版本；拆版本会破坏"三方言同版本号"项目规则。
- **D2 孤儿表与 mapping_* 用"存在即删"守卫**（不做行数复核）。依据：8 张孤儿表代码中
  无 `@Table` 实体（已 grep 核实）；`mapping_scheme`/`mapping_subscription` 功能经
  V20260920001 移除。任何行数的数据都无应用含义。
  替代方案：统一行数守卫——被否，多余复杂度且Flyway 无法对"跳过"发出警告，静默跳过反而掩盖问题。
- **D3 `interop_mapping`/`version_history` 三重守卫**：`dbo.T` 存在 AND `ext.T` 存在 AND
  `dbo.T` 行数=0 才 DROP。覆盖"某环境 personal 模式未加载 orm-team.xml、dbo 实为活表"
  的理论场景（该场景下 ext 副本通常不存在，或有数据）；不满足则跳过，
  与 design.md 第 119 行"DBA 事项"先例一致。
- **D4 SQL Server 按依赖序 DROP，不用 FK-cursor**。实测外键仅存在于 5 张 fhir 孤儿表
  集合内部，固定顺序 `fhir_binding` → `fhir_element` → `fhir_resource`/`fhir_value_set`/
  `fhir_code_system` → 其余 7 张即足够；FK-cursor（V20260920001 先例）为未知外键设计，
  此处已知全集，固定顺序更直观。若将来发现未知外键，迁移报错即为信号，人工补序。
- **D5 SQLite 白名单式枚举 10 张非活表**（8 孤儿 + `mapping_scheme`/`mapping_subscription`，
  `fhir_binding` 首次清理），不碰 `interop_mapping`/`version_history`。
  DM 同清单（owner 守卫 `MDR`，防御性检查 `EXT`；DM 无 `dbo` 概念，个人版不部署 DM）。
- **D6 迁移名 `V20261028001`**（现行最大版本 `20261023001` + 当日日期 2026-10-28）。

## Risks / Trade-offs

- [某存量环境 dbo 副本有数据，属活表] → D3 三重守卫跳过删除，现场保留；
  本机库已实测 0 行 + ext 副本存在，满足删除条件。
- [孤儿表在其他环境存在且被未知依赖引用] → 无实体即无 JPA 依赖；原生 SQL 侧
  `DatabaseEnvironment.EXT_TABLES` 不含这些表名（已核实），无路由指向 dbo。
- [DROP 顺序遗漏外键导致迁移失败] → 迁移在事务中失败即回滚，Flyway 标记失败可见，
  人工补序即可；幂等守卫支持修复后重跑。
- [全新空库/测试库执行 no-op] → 已验证清单与既有迁移（V20260901015 sqlite 已删 8 张
  中的 7 张、`V20260920001` 已删 mapping 两表）不冲突，`DROP IF EXISTS`/`IF EXISTS` 均幂等。

## Migration Plan

1. 新增三方言脚本（本变更 tasks 实施）。
2. 本机 SQL Server 库重启应用触发迁移；验证 `sys.tables`(dbo) 无业务表。
3. 应用冒烟：注册项列表、版本历史、映射管理页面；个人版 SQLite 冒烟：版本历史功能。
4. 回退：被删表均为 0 行死表，无数据回退需求；结构回退按历史脚本重建或备份恢复。

## Open Questions

无。个人版+SQL Server 但未加载 orm-team.xml 的环境是否存在活 dbo 副本，属 D3 守卫
天然覆盖的部署形态问题，不影响本设计。
