-- ============================================================================
-- V20261009011__Administered_item_link_indexes.sql  (SQL Server)
--
-- repair-administered-item-reference-documents-ui：为两张多对多关联表补 join 列索引。
--
-- 【为什么】registered_item_reference_document 与 registered_item_submission_record
-- 原先**无任何索引**（连主键都没有）。关联表为空时全表扫描不产生可观测代价，
-- 关联规模上万后条目列表接口劣化到数十秒级超时（2026-10-09）。
-- 手工建索引不能替代迁移固化，否则换环境重建库即丢失。
--
-- 【为什么暂不加 UNIQUE 约束】两张表语义上以 (两侧列) 组合为唯一键，但存量数据
-- 未验证无重复；贸然施加会在存量库上失败。按 database-portability 的
-- 「关联表收紧为唯一约束前 SHALL 先验证历史数据」要求，去重 + 收紧另行判定。
--
-- 幂等：索引已存在则跳过（动态取名，不硬编码）。
-- ============================================================================

SET NOCOUNT ON;

-- ① registered_item_reference_document
IF NOT EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID(N'mdr.registered_item_reference_document')
                  AND name = N'idx_rird_registered_item')
    CREATE INDEX idx_rird_registered_item
        ON mdr.registered_item_reference_document (registered_item_id);

IF NOT EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID(N'mdr.registered_item_reference_document')
                  AND name = N'idx_rird_reference_document')
    CREATE INDEX idx_rird_reference_document
        ON mdr.registered_item_reference_document (reference_document_id);

-- ② registered_item_submission_record
IF NOT EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID(N'mdr.registered_item_submission_record')
                  AND name = N'idx_risr_registered_item')
    CREATE INDEX idx_risr_registered_item
        ON mdr.registered_item_submission_record (registered_item_id);

IF NOT EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID(N'mdr.registered_item_submission_record')
                  AND name = N'idx_risr_submission_record')
    CREATE INDEX idx_risr_submission_record
        ON mdr.registered_item_submission_record (submission_record_id);