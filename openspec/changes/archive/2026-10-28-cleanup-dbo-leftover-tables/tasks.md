# 任务清单:dbo 遗留表清理(cleanup-dbo-leftover-tables)

## 1. 迁移脚本

- [x] 1.1 新增 `metadata-registry-web/src/main/resources/db/migration/sqlserver/V20261028001__Drop_dbo_leftover_tables.sql`：按依赖序守卫 DROP 12 张 dbo 表（`fhir_binding`→`fhir_element`→`fhir_resource`/`fhir_value_set`/`fhir_code_system`→其余 7 张），`interop_mapping`/`version_history` 附加 `ext` 副本存在且 dbo 副本 0 行的三重守卫；验证：脚本内容与 design D1/D3/D4 一致，逐表存在性守卫齐全（已实库执行：dbo 业务表 0 张，重跑全守卫 no-op）
- [x] 1.2 新增 `metadata-registry-web/src/main/resources/db/migration/sqlite/V20261028001__Drop_dbo_leftover_tables.sql`：`DROP TABLE IF EXISTS` 白名单 10 张（8 孤儿 + `mapping_scheme`/`mapping_subscription`），不含 `interop_mapping`/`version_history`；验证：清单与 design D5 逐字一致（已用 Python sqlite3 内存库实测：10 张全删、活表保留、二次执行无错）
- [x] 1.3 新增 `metadata-registry-web/src/main/resources/db/migration/dm/V20261028001__Drop_dbo_leftover_tables.sql`：PL/SQL 块 + `ALL_TABLES` owner 守卫（`MDR`，防御性检查 `EXT`）删除同 10 张非活表，idiom 与 `V20260901015` dm 脚本一致；验证：与 design D5 一致，不触碰 `INTEROP_MAPPING`/`VERSION_HISTORY`（静态结构测试 `DropDboLeftoverTablesMigrationTest` 守卫）
- [x] 1.4 新增静态结构测试 `DropDboLeftoverTablesMigrationTest`（三方言齐备性 / SQL Server 守卫结构 / SQLite 白名单 / DM 白名单），idiom 同 `DropMdrRegistrationTransitionOrphansMigrationTest`

## 2. 构建与既有守卫回归

- [x] 2.1 `./mvnw -pl metadata-registry-web -am test` 全量 1659 测试：较变更前净改善（14 失败→12 失败，两个基线守卫测试由红转绿，新增 4 个迁移结构测试全过）；剩余 12 个失败与变更前逐项一致（RegistrySpecification mock、Workspace 服务×9、并发 SQLite 锁×2），为既有问题，另行跟进

## 3. 实库验证（本机 SQL Server `metadata_registry`）

- [x] 3.1 重启本机应用触发迁移，`ext.flyway_schema_history` 出现 `20261028001` 且 success=1；验证：sqlcmd 查询确认（曾遇历史表 `installed_on` 默认值丢失导致 515，已在脚本内自愈修复后成功记录）
- [x] 3.2 `sys.tables`(dbo) 无业务表，`mdr`/`ext` 对象数不变、`ext.interop_mapping`/`ext.version_history` 仍在；验证：sqlcmd 查询确认（dbo 业务表 0 张，活表无损）
- [x] 3.3 应用冒烟：注册项列表、版本历史、映射管理页面正常；个人版 SQLite 冒烟：版本历史功能正常；验证：页面/API 实测（administered-items / item-mappings / items/{id}/versions 均 200，版本历史走 ext.version_history 证明活表可用）

## 4. 收尾

- [x] 4.1 编辑前影响分析（gitnexus impact：`FreshDatabaseInitializer` 上游 2 调用方、risk LOW、无执行流受影响）与变更分析（detect_changes：变更集巨大系工作树既有脏改，本变更足迹仅 FreshDatabaseInitializer 常量 + 新测试类，已核）按 AGENTS.md 执行并记录
- [x] 4.2 OpenSpec 归档（`openspec archive` 已落地为 `2026-10-28-cleanup-dbo-leftover-tables`，主 spec `database-portability` +1 需求）与 `.superpowers-memory` 会话日志
