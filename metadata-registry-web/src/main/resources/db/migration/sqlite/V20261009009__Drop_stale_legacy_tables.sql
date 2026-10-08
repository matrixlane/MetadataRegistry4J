-- ============================================================================
-- V20261009009__Drop_stale_legacy_tables.sql  (SQLite)
--
-- cleanup-stale-legacy-tables（2026-10-09）：逻辑同 SQL Server 版——清除
-- V20261009005/09006 已更名/删除、又被旧模型 jar 重建的 11 张残留表。
-- SQLite 历史副本经核实无残留（existing.db at 09007 确认），干净库空转；
-- DROP TABLE IF EXISTS 幂等可重入。裸表名（SQLite 无 schema 概念）。
-- ============================================================================

DROP TABLE IF EXISTS conceptual_domain_dec;
DROP TABLE IF EXISTS definition_context_relation;
DROP TABLE IF EXISTS designation_context_relation;
DROP TABLE IF EXISTS designation_naming_convention;
DROP TABLE IF EXISTS item_mapping_subject;
DROP TABLE IF EXISTS item_mapping_object;
DROP TABLE IF EXISTS namespace_naming_convention;
DROP TABLE IF EXISTS unit_of_measure_measure_class;
DROP TABLE IF EXISTS submission_record_registered_item;
DROP TABLE IF EXISTS value_meaning_permissible_value_meaning;
DROP TABLE IF EXISTS ws_import_run;
