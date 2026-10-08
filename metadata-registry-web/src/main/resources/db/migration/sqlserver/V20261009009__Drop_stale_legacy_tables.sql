-- ============================================================================
-- V20261009009__Drop_stale_legacy_tables.sql  (SQL Server)
--
-- cleanup-stale-legacy-tables（2026-10-09）：mdr-standard-compliance-cleanup
-- （V20261009005/09006，应用于 2026-10-08 04:02/04:10）已更名/删除下列旧名表，
-- 但 10-08 一个携带旧实体模型的 jar 启动（sys.objects 建表时间 05:48:21 /
-- 23:21:28 晚于迁移应用时间）将其重建。经逐一核实（全代码库 @Table/@JoinTable/
-- 迁移脚本零引用 + 无现行表外键引用），以下 11 张表均不在当前实体模型中，
-- 予以清除。现行表（含 ext schema 卫星表 ext.contact/ext.registrar 与更名
-- 目标表）一律不动。全守卫、幂等可重入；全新库空转。
--
-- 处置依据（rename 目标已核实存在于 mdr 且承载现行数据）：
--   conceptual_domain_dec              → data_element_concept_domain
--   definition_context_relation        → definition_context
--   designation_context_relation       → designation_context
--   designation_naming_convention      → naming_convention_conformance
--   item_mapping_subject               → subject_mapping
--   item_mapping_object                → object_mapping
--   namespace_naming_convention        → naming_convention_utilization
--   unit_of_measure_measure_class      → unit_of_measure_class
--   submission_record_registered_item  → registered_item_submission_record
--   value_meaning_permissible_value_meaning  V20261009005 明文"冗余映射表"已删
--                                            （残留 4674 行为旧 jar 并行写入）
--   ws_import_run                      代码/迁移零引用的更老 WIP 残留（1 行死数据）
--
-- 注意：若再次以旧模型 jar 启动，这些表会被重建——属发布纪律，非本迁移职责。
-- ============================================================================

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'conceptual_domain_dec')
    DROP TABLE mdr.conceptual_domain_dec;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'definition_context_relation')
    DROP TABLE mdr.definition_context_relation;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'designation_context_relation')
    DROP TABLE mdr.designation_context_relation;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'designation_naming_convention')
    DROP TABLE mdr.designation_naming_convention;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'item_mapping_subject')
    DROP TABLE mdr.item_mapping_subject;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'item_mapping_object')
    DROP TABLE mdr.item_mapping_object;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'namespace_naming_convention')
    DROP TABLE mdr.namespace_naming_convention;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'unit_of_measure_measure_class')
    DROP TABLE mdr.unit_of_measure_measure_class;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'submission_record_registered_item')
    DROP TABLE mdr.submission_record_registered_item;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'value_meaning_permissible_value_meaning')
    DROP TABLE mdr.value_meaning_permissible_value_meaning;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'ws_import_run')
    DROP TABLE mdr.ws_import_run;
