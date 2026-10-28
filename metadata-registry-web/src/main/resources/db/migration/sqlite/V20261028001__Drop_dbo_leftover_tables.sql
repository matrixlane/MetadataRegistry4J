-- dbo 遗留表清理（cleanup-dbo-leftover-tables）SQLite 侧
-- SQLite 无 schema 概念，清理主库中同名残留。幂等：DROP TABLE IF EXISTS。
-- 严禁删除 interop_mapping / version_history —— 个人版 SQLite 主库下二者为活表。

DROP TABLE IF EXISTS fhir_binding;
DROP TABLE IF EXISTS fhir_element;
DROP TABLE IF EXISTS fhir_resource;
DROP TABLE IF EXISTS fhir_value_set;
DROP TABLE IF EXISTS fhir_code_system;
DROP TABLE IF EXISTS data_quality_task;
DROP TABLE IF EXISTS notification_preference;
DROP TABLE IF EXISTS notification_record;
DROP TABLE IF EXISTS mapping_scheme;
DROP TABLE IF EXISTS mapping_subscription;
