-- ============================================================================
-- V20261009005__Mdr_standard_compliance_cleanup.sql  (SQL Server)
--
-- mdr schema 标准一致性清理（mdr-standard-compliance-cleanup）：
--   1. 删除测试残留表 ×3 与冗余映射表 value_meaning_permissible_value_meaning；
--   2. 9 张表按 ISO/IEC 11179-3/31 标准名更名；
--   3. registration_transition_* 三表迁 ext schema（非标准工作流表）；
--   4. 删除 16 个非标准列（先动态清除引用其的 FK/CHECK/DEFAULT 约束）。
--
-- 全部幂等守卫：既有库（缺 2026-09-01 剥离批次）与全新库均可安全应用/重入。
-- flyway_schema_history 归位 ext 由 FlywayHistoryRelocator 在启动期完成，不在此重复。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 测试残留表与冗余表
-- ---------------------------------------------------------------------------
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='definition_service_test$test_item')
    DROP TABLE mdr.[definition_service_test$test_item];
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='designation_service_test$test_item')
    DROP TABLE mdr.[designation_service_test$test_item];
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='item_administration_service_test$test_item')
    DROP TABLE mdr.[item_administration_service_test$test_item];
-- 冗余：Permissible_Value_Meaning 已含 value_meaning_id 列承载同一绑定（ValueMeaning 实体改走 mappedBy）
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='value_meaning_permissible_value_meaning')
    DROP TABLE mdr.value_meaning_permissible_value_meaning;

-- ---------------------------------------------------------------------------
-- 2. 表更名（旧名存在且新名不存在才执行）
-- ---------------------------------------------------------------------------
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='conceptual_domain_dec')
    AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='data_element_concept_domain')
    EXEC sp_rename 'mdr.conceptual_domain_dec', 'data_element_concept_domain';
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='designation_context_relation')
    AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='designation_context')
    EXEC sp_rename 'mdr.designation_context_relation', 'designation_context';
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='definition_context_relation')
    AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='definition_context')
    EXEC sp_rename 'mdr.definition_context_relation', 'definition_context';
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='item_mapping_subject')
    AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='subject_mapping')
    EXEC sp_rename 'mdr.item_mapping_subject', 'subject_mapping';
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='item_mapping_object')
    AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='object_mapping')
    EXEC sp_rename 'mdr.item_mapping_object', 'object_mapping';
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='designation_naming_convention')
    AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='naming_convention_conformance')
    EXEC sp_rename 'mdr.designation_naming_convention', 'naming_convention_conformance';
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='namespace_naming_convention')
    AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='naming_convention_utilization')
    EXEC sp_rename 'mdr.namespace_naming_convention', 'naming_convention_utilization';
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='unit_of_measure_measure_class')
    AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='unit_of_measure_class')
    EXEC sp_rename 'mdr.unit_of_measure_measure_class', 'unit_of_measure_class';
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='submission_record_registered_item')
    AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='registered_item_submission_record')
    EXEC sp_rename 'mdr.submission_record_registered_item', 'registered_item_submission_record';

-- ---------------------------------------------------------------------------
-- 3. registration_transition_* 三表迁 ext（JPA 经 orm-team.xml 登记 schema="ext"）
-- ---------------------------------------------------------------------------
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='registration_transition_rule')
    AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='ext' AND TABLE_NAME='registration_transition_rule')
    ALTER SCHEMA ext TRANSFER mdr.registration_transition_rule;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='registration_transition_approval')
    AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='ext' AND TABLE_NAME='registration_transition_approval')
    ALTER SCHEMA ext TRANSFER mdr.registration_transition_approval;
-- registration_transition_rule_approver 为 @ElementCollection 子表，随主表同迁
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='registration_transition_rule_approver')
    AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='ext' AND TABLE_NAME='registration_transition_rule_approver')
    ALTER SCHEMA ext TRANSFER mdr.registration_transition_rule_approver;

-- ---------------------------------------------------------------------------
-- 4. 删非标准列（逐列：清 DEFAULT → 清 FK → 清 CHECK → DROP COLUMN，全守卫）
--    全部列经审计 0 行非空，零数据搬迁。
-- ---------------------------------------------------------------------------
DECLARE @targets TABLE(id INT IDENTITY, tbl SYSNAME, col SYSNAME);
INSERT INTO @targets(tbl, col) VALUES
 ('namespace', 'identifier_input_mode'),
 ('namespace', 'manual_identifier_pattern'),
 ('namespace', 'version_input_mode'),
 ('namespace', 'versioning_policy'),
 ('scoped_identifier', 'identifier_creation_mode'),
 ('scoped_identifier', 'version_creation_mode'),
 ('scoped_identifier', 'version_status'),
 ('scoped_identifier', 'created_at'),
 ('scoped_identifier', 'published_at'),
 ('scoped_identifier', 'deprecated_at'),
 ('scoped_identifier', 'obsolete_at'),
 ('naming_convention', 'rules_json'),
 ('language_identification', 'enabled'),
 ('contact', 'account_id'),
 ('data_type_scheme', 'reference'),
 ('slot', 'value'),
 ('slot', 'value_datatype'),
 ('role', 'email_address'),
 ('role', 'phone_number'),
 ('designation', 'acceptability'),
 ('definition', 'acceptability'),
 ('submission_record', 'source');

DECLARE @i INT = 1, @max INT, @tbl SYSNAME, @col SYSNAME, @sql NVARCHAR(1000);
SELECT @max = MAX(id) FROM @targets;

-- 4.1 集合式收集待删约束（DEFAULT/FK/CHECK，CHECK 按定义文本涉及列匹配），避免嵌套游标
DECLARE @constraints TABLE(id INT IDENTITY, stmt NVARCHAR(1000));
INSERT INTO @constraints(stmt)
    SELECT N'ALTER TABLE mdr.' + QUOTENAME(OBJECT_NAME(d.parent_object_id))
         + N' DROP CONSTRAINT ' + QUOTENAME(d.name)
    FROM sys.default_constraints d
    JOIN sys.columns c ON c.object_id = d.parent_object_id AND c.column_id = d.parent_column_id
    JOIN @targets t ON t.tbl = OBJECT_NAME(d.parent_object_id) AND t.col = c.name
UNION ALL
    SELECT N'ALTER TABLE mdr.' + QUOTENAME(OBJECT_NAME(fk.parent_object_id))
         + N' DROP CONSTRAINT ' + QUOTENAME(fk.name)
    FROM sys.foreign_keys fk
    JOIN sys.foreign_key_columns k ON k.constraint_object_id = fk.object_id
    JOIN sys.columns c ON c.object_id = k.parent_object_id AND c.column_id = k.parent_column_id
    JOIN @targets t ON t.tbl = OBJECT_NAME(fk.parent_object_id) AND t.col = c.name
UNION ALL
    SELECT N'ALTER TABLE mdr.' + QUOTENAME(OBJECT_NAME(cc.parent_object_id))
         + N' DROP CONSTRAINT ' + QUOTENAME(cc.name)
    FROM sys.check_constraints cc
    JOIN @targets t ON t.tbl = OBJECT_NAME(cc.parent_object_id)
        AND cc.definition LIKE N'%' + t.col + N'%';

-- 4.2 逐条删约束（TOP 1 + DELETE 循环，无游标，DDL 重编译安全）
WHILE EXISTS (SELECT 1 FROM @constraints)
BEGIN
    SELECT TOP 1 @sql = stmt FROM @constraints ORDER BY id;
    DELETE FROM @constraints WHERE id = (SELECT TOP 1 id FROM @constraints ORDER BY id);
    EXEC(@sql);
END

-- 4.3 逐列 DROP COLUMN（仅当列仍存在）
WHILE @i <= @max
BEGIN
    SELECT @tbl = tbl, @col = col FROM @targets WHERE id = @i;
    IF COL_LENGTH('mdr.' + @tbl, @col) IS NOT NULL
    BEGIN
        SET @sql = N'ALTER TABLE mdr.' + QUOTENAME(@tbl) + N' DROP COLUMN ' + QUOTENAME(@col);
        EXEC(@sql);
    END
    SET @i = @i + 1;
END
