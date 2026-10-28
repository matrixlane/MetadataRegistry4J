# 2026-10-28 dbo 遗留表清理（cleanup-dbo-leftover-tables）

## 背景

用户在 SSMS 看到 `dbo` 下 12 张非 ISO/IEC 11179 业务表（fhir_*、data_quality_task、
notification_*、mapping_*、interop_mapping、version_history），质疑为何不在 `ext`。
调查确认：2026-05 未限定表名建表落入 dbo；2026-09-01 mdr/ext 拆分只守卫
`TABLE_SCHEMA='mdr'`，dbo 残留至今。规范（AGENTS.md 数据模型分层规则 +
database-portability spec）一直要求这些表归 ext。

## 处置

OpenSpec 变更 `cleanup-dbo-leftover-tables`：新增三方言同版本
`V20261028001__Drop_dbo_leftover_tables.sql`（sqlserver/sqlite/dm）+ 静态结构测试
`DropDboLeftoverTablesMigrationTest`。

- 8 张无实体孤儿表 + mapping 两表：存在即删。
- interop_mapping/version_history：三重守卫（dbo 存在 + ext 存在 + dbo 0 行，
  行数用 `sys.partitions + OBJECT_ID` 判定，避免编译期引用不存在表）。
- 联动修复：`FreshDatabaseInitializer.FRESH_BASELINE_VERSION` → 20261028001，
  `FreshDatabaseInitializerTest` 三方言最大版本断言同步（本变更使三方言版本自
  20261009012 以来首次对齐，顺带修复 e039f893 起红着的 `CommonBaselineGuardTest`）。

## 关键教训（复用价值高）

1. **sqlcmd 按控制台代码页（GBK）读文件**，UTF-8 含中文注释的脚本会把迁移执行出
   诡异语法错误（"关键字 'AND' 附近有语法错误"）；Flyway/JDBC 读 UTF-8 无此问题。
   手工验证需 `iconv -f UTF-8 -t GBK` 转码后喂给 sqlcmd。
2. **Flyway 写历史表省略 `installed_on` 列，依赖建表时的 `DEFAULT (GETDATE())`**。
   本库该默认值在历史某刻被弄丢 → 报 515 "不能将值 NULL"。日志只显示截断的
   "不能将值 NULL"，**用 Extended Events（error_reported + ring_buffer）抓完整错误
   和真实 INSERT 语句**定位到列名。修复模式：迁移脚本开头幂等补默认值守卫
   （`sys.default_constraints` 检查 + `ADD DEFAULT (GETDATE()) FOR`）。
3. **重启后端前必须先 `taskkill` 并确认端口释放**，否则 spring-boot repackage
   因 Windows 文件锁 rename 失败（且 thin jar 会先覆盖 fat jar，留下坏包）。
4. **新增迁移的联动清单**：三方言同版本脚本 + `FreshDatabaseInitializer.FRESH_BASELINE_VERSION`
   + `FreshDatabaseInitializerTest` 硬编码最大版本断言 + 结构测试（仿
   `DropMdrRegistrationTransitionOrphansMigrationTest`）。

## 遗留

- 全量测试 12 个既有失败（RegistrySpecification mock、Workspace 服务×9、并发
  SQLite 锁×2），与本变更无关（变更前后逐项一致），需另行排查。
- ~~OpenSpec 归档（4.2）待代码评审后执行。~~

## 评审与归档（2026-10-28 收尾）

双独立只读评审之一完成（数据安全/幂等/FK 序/方言一致性/项目规则五维）：
**0 BLOCKER / 0 MAJOR**，M1–M5 为知情取舍（CCI 行数盲区、守卫跳过无提示、未知 FK
报错即信号、515 自愈时序边界、DM owner 口径），M6 测试收紧已受理（SQLite/DM 精确
10 张集合断言 + SQL Server 先子后父顺序断言）。另一评审因子代理 429（Token Plan
上限）改由主代理自查：基线提升跳过的迁移集合（20261009012, 20261028001] 三方言
逐一代入均为有守卫清理型 no-op，全新库安全；`application-personal.yml` 过时基线
注释已修；proposal 中 V20260603001 叙述已按实修正（该脚本实为搬运当时全部 dbo
用户表，12 张系此后重建/残留）。评审修复后定向测试全绿，`openspec archive` 落地为
`2026-10-28-cleanup-dbo-leftover-tables`，主 spec `database-portability` +1 需求，
`openspec validate --all` 74/74（唯一 WARNING 为 overview 能力既有占位 Purpose，非本次引入）。
**未 git commit（待用户确认；工作树含并行会话脏改，提交按变更分组）。**
