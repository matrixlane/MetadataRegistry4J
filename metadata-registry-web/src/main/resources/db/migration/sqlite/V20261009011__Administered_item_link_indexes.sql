-- ============================================================================
-- V20261009011__Administered_item_link_indexes.sql  (SQLite)
--
-- repair-administered-item-reference-documents-ui：为两张多对多关联表补 join 列索引。
-- 与 SQL Server 版同源，差异仅在 SQLite 无 schema 限定、且 CREATE INDEX 支持 IF NOT EXISTS。
-- 详见 SQL Server 版文件头的「为什么」。
-- 幂等：IF NOT EXISTS。
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_rird_registered_item
    ON registered_item_reference_document (registered_item_id);

CREATE INDEX IF NOT EXISTS idx_rird_reference_document
    ON registered_item_reference_document (reference_document_id);

CREATE INDEX IF NOT EXISTS idx_risr_registered_item
    ON registered_item_submission_record (registered_item_id);

CREATE INDEX IF NOT EXISTS idx_risr_submission_record
    ON registered_item_submission_record (submission_record_id);
