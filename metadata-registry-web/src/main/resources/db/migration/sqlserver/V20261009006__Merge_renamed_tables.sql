-- ============================================================================
-- V20261009006__Merge_renamed_tables.sql  (SQL Server)
--
-- mdr-standard-compliance-cleanup 补充：V20261009005 的更名阶段在"ddl-auto 先于
-- Flyway"顺序（FlywayAfterJpaConfig 设计）下，因新名表已被 ddl-auto 预建而按守卫跳过，
-- 旧名表与其数据残留。本迁移按"新名表存在则合并去重后删旧表，否则 sp_rename"收尾，
-- 两种执行顺序（Flyway 先 / ddl-auto 先）均收敛到同一终态。全部幂等守卫。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 先清除 9 张旧名表上的 FK/CHECK 约束（集合式收集，避免嵌套游标）
-- ---------------------------------------------------------------------------
DECLARE @oldTables TABLE(tbl SYSNAME);
INSERT INTO @oldTables(tbl) VALUES
 ('conceptual_domain_dec'), ('designation_context_relation'), ('definition_context_relation'),
 ('item_mapping_subject'), ('item_mapping_object'), ('designation_naming_convention'),
 ('namespace_naming_convention'), ('unit_of_measure_measure_class'), ('submission_record_registered_item');

DECLARE @constraints TABLE(id INT IDENTITY, stmt NVARCHAR(1000));
INSERT INTO @constraints(stmt)
    SELECT N'ALTER TABLE ' + QUOTENAME(OBJECT_SCHEMA_NAME(fk.parent_object_id)) + N'.'
         + QUOTENAME(OBJECT_NAME(fk.parent_object_id)) + N' DROP CONSTRAINT ' + QUOTENAME(fk.name)
    FROM sys.foreign_keys fk
    JOIN @oldTables t ON t.tbl = OBJECT_NAME(fk.parent_object_id)
        AND OBJECT_SCHEMA_NAME(fk.parent_object_id) = 'mdr'
UNION ALL
    SELECT N'ALTER TABLE mdr.' + QUOTENAME(OBJECT_NAME(cc.parent_object_id)) + N' DROP CONSTRAINT ' + QUOTENAME(cc.name)
    FROM sys.check_constraints cc
    JOIN @oldTables t ON t.tbl = OBJECT_NAME(cc.parent_object_id)
        AND OBJECT_SCHEMA_NAME(cc.parent_object_id) = 'mdr';

DECLARE @sql NVARCHAR(1000);
WHILE EXISTS (SELECT 1 FROM @constraints)
BEGIN
    SELECT TOP 1 @sql = stmt FROM @constraints ORDER BY id;
    DELETE FROM @constraints WHERE id = (SELECT TOP 1 id FROM @constraints ORDER BY id);
    EXEC(@sql);
END

-- ---------------------------------------------------------------------------
-- 2. 逐对：新名存在 → 全行去重合并后删旧表；新名缺失 → sp_rename
-- ---------------------------------------------------------------------------
-- 2.1 data_element_concept_domain（纯连接表，全行去重）
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='conceptual_domain_dec')
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='data_element_concept_domain')
        EXEC sp_rename 'mdr.conceptual_domain_dec', 'data_element_concept_domain';
    ELSE
    BEGIN
        INSERT INTO mdr.data_element_concept_domain (conceptual_domain_id, data_element_concept_id)
        SELECT o.conceptual_domain_id, o.data_element_concept_id FROM mdr.conceptual_domain_dec o
        WHERE NOT EXISTS (SELECT 1 FROM mdr.data_element_concept_domain n
                          WHERE n.conceptual_domain_id = o.conceptual_domain_id
                            AND n.data_element_concept_id = o.data_element_concept_id);
        DROP TABLE mdr.conceptual_domain_dec;
    END
END

-- 2.2 designation_context（带 id PK，按 id 去重）
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='designation_context_relation')
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='designation_context')
        EXEC sp_rename 'mdr.designation_context_relation', 'designation_context';
    ELSE
    BEGIN
        INSERT INTO mdr.designation_context (id, acceptability, context_id, designation_id)
        SELECT o.id, o.acceptability, o.context_id, o.designation_id FROM mdr.designation_context_relation o
        WHERE NOT EXISTS (SELECT 1 FROM mdr.designation_context n WHERE n.id = o.id);
        DROP TABLE mdr.designation_context_relation;
    END
END

-- 2.3 definition_context（带 id PK，按 id 去重）
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='definition_context_relation')
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='definition_context')
        EXEC sp_rename 'mdr.definition_context_relation', 'definition_context';
    ELSE
    BEGIN
        INSERT INTO mdr.definition_context (id, acceptability, context_id, definition_id)
        SELECT o.id, o.acceptability, o.context_id, o.definition_id FROM mdr.definition_context_relation o
        WHERE NOT EXISTS (SELECT 1 FROM mdr.definition_context n WHERE n.id = o.id);
        DROP TABLE mdr.definition_context_relation;
    END
END

-- 2.4 subject_mapping（带 id PK，按 id 去重）
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='item_mapping_subject')
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='subject_mapping')
        EXEC sp_rename 'mdr.item_mapping_subject', 'subject_mapping';
    ELSE
    BEGIN
        INSERT INTO mdr.subject_mapping (id, item_mapping_id, item_id, mapping_variable_name)
        SELECT o.id, o.item_mapping_id, o.item_id, o.mapping_variable_name FROM mdr.item_mapping_subject o
        WHERE NOT EXISTS (SELECT 1 FROM mdr.subject_mapping n WHERE n.id = o.id);
        DROP TABLE mdr.item_mapping_subject;
    END
END

-- 2.5 object_mapping（带 id PK，按 id 去重）
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='item_mapping_object')
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='object_mapping')
        EXEC sp_rename 'mdr.item_mapping_object', 'object_mapping';
    ELSE
    BEGIN
        INSERT INTO mdr.object_mapping (id, item_mapping_id, item_id, mapping_variable_name)
        SELECT o.id, o.item_mapping_id, o.item_id, o.mapping_variable_name FROM mdr.item_mapping_object o
        WHERE NOT EXISTS (SELECT 1 FROM mdr.object_mapping n WHERE n.id = o.id);
        DROP TABLE mdr.item_mapping_object;
    END
END

-- 2.6 naming_convention_conformance（纯连接表，全行去重）
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='designation_naming_convention')
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='naming_convention_conformance')
        EXEC sp_rename 'mdr.designation_naming_convention', 'naming_convention_conformance';
    ELSE
    BEGIN
        INSERT INTO mdr.naming_convention_conformance (designation_id, naming_convention_id)
        SELECT o.designation_id, o.naming_convention_id FROM mdr.designation_naming_convention o
        WHERE NOT EXISTS (SELECT 1 FROM mdr.naming_convention_conformance n
                          WHERE n.designation_id = o.designation_id
                            AND n.naming_convention_id = o.naming_convention_id);
        DROP TABLE mdr.designation_naming_convention;
    END
END

-- 2.7 naming_convention_utilization（纯连接表，全行去重）
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='namespace_naming_convention')
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='naming_convention_utilization')
        EXEC sp_rename 'mdr.namespace_naming_convention', 'naming_convention_utilization';
    ELSE
    BEGIN
        INSERT INTO mdr.naming_convention_utilization (namespace_id, naming_convention_id)
        SELECT o.namespace_id, o.naming_convention_id FROM mdr.namespace_naming_convention o
        WHERE NOT EXISTS (SELECT 1 FROM mdr.naming_convention_utilization n
                          WHERE n.namespace_id = o.namespace_id
                            AND n.naming_convention_id = o.naming_convention_id);
        DROP TABLE mdr.namespace_naming_convention;
    END
END

-- 2.8 unit_of_measure_class（纯连接表，全行去重）
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='unit_of_measure_measure_class')
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='unit_of_measure_class')
        EXEC sp_rename 'mdr.unit_of_measure_measure_class', 'unit_of_measure_class';
    ELSE
    BEGIN
        INSERT INTO mdr.unit_of_measure_class (unit_of_measure_id, measure_class_id)
        SELECT o.unit_of_measure_id, o.measure_class_id FROM mdr.unit_of_measure_measure_class o
        WHERE NOT EXISTS (SELECT 1 FROM mdr.unit_of_measure_class n
                          WHERE n.unit_of_measure_id = o.unit_of_measure_id
                            AND n.measure_class_id = o.measure_class_id);
        DROP TABLE mdr.unit_of_measure_measure_class;
    END
END

-- 2.9 registered_item_submission_record（纯连接表，全行去重）
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='submission_record_registered_item')
BEGIN
    IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='registered_item_submission_record')
        EXEC sp_rename 'mdr.submission_record_registered_item', 'registered_item_submission_record';
    ELSE
    BEGIN
        INSERT INTO mdr.registered_item_submission_record (submission_record_id, registered_item_id)
        SELECT o.submission_record_id, o.registered_item_id FROM mdr.submission_record_registered_item o
        WHERE NOT EXISTS (SELECT 1 FROM mdr.registered_item_submission_record n
                          WHERE n.submission_record_id = o.submission_record_id
                            AND n.registered_item_id = o.registered_item_id);
        DROP TABLE mdr.submission_record_registered_item;
    END
END
