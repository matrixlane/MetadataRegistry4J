# Design: cleanup-stale-legacy-tables

## 事实依据（2026-10-09 只读核实）

- 残留判定双证据：① `sys.objects.create_date` 晚于 V20261009005（2026-10-08 04:02:26）与 V20261009006（04:10:41）的应用时间（05:48:21 批次 10 张 + `ws_import_run` 23:21:28）；② 全代码库 `@Table`/`@JoinTable`/迁移脚本零引用（逐一 grep）。
- 9 张旧名表的 rename 目标全部存在于 mdr 且承载现行数据（行数核实：definition_context 2 行、designation_context 2 行，其余 0）；`value_meaning_permissible_value_meaning` 被 V20261009005 明文定为"冗余映射表"删除。
- `sys.foreign_key_columns` 查询确认**无现行表外键引用**这些残留表，DROP 无阻塞。
- ext.contact / ext.registrar 虽同建于 10-08 06:06（旧进程重建），但属当前实体模型（orm-team.xml 登记的 V20261009001/09002 卫星表），**不在清理范围**——"建表时间晚"不是充分判据，"不在当前模型"才是。

## 技术决策

### 决策 1：全守卫 DROP，幂等可重入

- SQL Server：`IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME=...) DROP TABLE mdr.xxx;`（沿用 V20261009005 模式）。
- SQLite：`DROP TABLE IF EXISTS xxx;` ×11（裸表名）。
- 达梦：`DECLARE n NUMBER;` 块内 `SELECT COUNT(*) FROM ALL_TABLES WHERE OWNER='MDR' AND TABLE_NAME=...` 守卫 + `EXECUTE IMMEDIATE 'DROP TABLE MDR.XXX CASCADE CONSTRAINTS'`（沿用 V20261009006 dm 模式）；逐表独立块，单表失败不影响其余（块内 `EXCEPTION WHEN OTHERS THEN NULL` 为项目达梦惯例）。
- 全新库 / 无残留的库全部空转；可重复执行。

### 决策 2：删除前不再备份表内数据

10 张空表无数据可备；`value_meaning_permissible_value_meaning` 的 4674 行是旧 jar 对已声明冗余的映射的并行写入，现行语义数据由 09005 之后的模型结构承载；`ws_import_run` 1 行为无代码消费的 WIP 残留。且真实库整体回滚备份（scratch/migtest/*.bak）仍在保留期内。

## 风险与对策

| 风险 | 对策 |
|---|---|
| 误删现行表 | 白名单式枚举 11 张表（逐一 grep + 模型核对）；ext.* 与 rename 目标明确排除；DROP 前已核实无 FK 引用 |
| 旧 jar 再运行重建残留 | 非本迁移职责；已在迁移头注与 journal 记录，属发布纪律 |
| 达梦无实例 | 脚本审查 + sqlite/sqlserver 冒烟代替（项目惯例） |
