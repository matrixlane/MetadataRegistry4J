# Tasks: cleanup-stale-legacy-tables

## 1. 三方言迁移 V20261009009

- [x] 1.1 SQL Server：11 张残留表全守卫 DROP（INFORMATION_SCHEMA.TABLES 存在性判断，沿用 V20261009005 模式），mdr 全限定
- [x] 1.2 SQLite：11 张残留表 `DROP TABLE IF EXISTS`（裸表名）
- [x] 1.3 达梦：11 张残留表 ALL_TABLES 计数守卫 + `DROP TABLE MDR.XXX CASCADE CONSTRAINTS`（沿用 V20261009006 dm 模式），逐表独立块
- [x] 1.4 表清单与处置依据写入迁移头注（rename 目标映射 + 冗余声明 + ws_import_run 零引用证据）

## 2. 验证

- [x] 2.1 SQL Server migtest 全新恢复库（含全部 11 张残留表）：应用迁移 → 11 张全删、rename 目标与 ext 卫星表行数不变；二次执行幂等
- [x] 2.2 SQLite 合成含残留表的库：应用迁移 → 清除且幂等；干净库（existing.db at 09007）空转
- [x] 2.3 真实 SQL Server 库应用（随新 jar 启动自动执行），复核 11 张消失 + 现行表行数抽查
- [x] 2.4 `openspec validate` 通过；`detect-changes` 无 partial

## 3. 收尾

- [x] 3.1 session journal 追加记录；CURRENT_STATE 更新
- [x] 3.2 归档（openspec archive）并按变更分组提交
