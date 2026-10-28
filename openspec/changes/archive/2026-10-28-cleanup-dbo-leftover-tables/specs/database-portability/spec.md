# database-portability 规格增量（cleanup-dbo-leftover-tables）

## ADDED Requirements

### Requirement: SQL Server dbo schema 零业务表

SQL Server 部署（含 team 与 personal）的 `dbo` schema SHALL NOT 承载任何业务表。
标准模型表归 `mdr` schema，扩展表归 `ext` schema（见「MDR/EXT schema 物理隔离」）。
存量库中因历史迁移（未限定表名时代）残留在 `dbo` 下的表 SHALL 由幂等迁移清理，
清理不得删除或修改 `mdr`/`ext` 下的任何对象。

#### Scenario: 迁移后 dbo 无业务表

- **GIVEN** SQL Server 存量库的 `dbo` schema 下存在 `fhir_resource`、`fhir_element`、
  `fhir_value_set`、`fhir_code_system`、`fhir_binding`、`data_quality_task`、
  `notification_preference`、`notification_record`、`mapping_scheme`、
  `mapping_subscription`、`interop_mapping`、`version_history` 中的任意张
- **WHEN** 应用启动执行迁移 `V20261028001`
- **THEN** 上述各表被删除，`dbo` 下不再存在业务表；`mdr`/`ext` 下的同名表不受影响

#### Scenario: 迁移幂等可重放

- **GIVEN** 迁移 `V20261028001` 已执行过一次（或目标库为全新空库、目标表本不存在）
- **WHEN** 再次执行该迁移的脚本内容
- **THEN** 所有删除操作因存在性守卫成为 no-op，无报错、无重复删除行为

#### Scenario: 活表 dbo 副本受三重守卫保护

- **GIVEN** 某环境 `dbo.interop_mapping`（或 `dbo.version_history`）存在，
  但满足以下任一条件：`ext` 下同名表不存在，或 `dbo` 副本行数大于 0
- **WHEN** 执行迁移 `V20261028001`
- **THEN** 该表被跳过、保留原状（该环境归 DBA 人工处置）

#### Scenario: SQLite 个人版活表不受影响

- **GIVEN** SQLite（个人版）数据库，`interop_mapping` 与 `version_history` 为
  主库活表
- **WHEN** 执行迁移 `V20261028001` 的 SQLite 脚本
- **THEN** `interop_mapping`、`version_history` 仍然存在；仅
  `fhir_resource`、`fhir_element`、`fhir_value_set`、`fhir_code_system`、
  `fhir_binding`、`data_quality_task`、`notification_preference`、
  `notification_record`、`mapping_scheme`、`mapping_subscription`
  被 `DROP TABLE IF EXISTS` 清理
