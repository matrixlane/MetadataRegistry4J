-- ============================================================================
-- V20261009011__Administered_item_link_indexes.sql  (达梦 DM8)
--
-- repair-administered-item-reference-documents-ui：为两张多对多关联表补 join 列索引。
-- 与 SQL Server 版同源，差异为全限定 schema 写法与动态 DDL（EXECUTE IMMEDIATE）。
-- 详见 SQL Server 版文件头的「为什么」与「为什么暂不加 UNIQUE 约束」。
--
-- 幂等：Flyway 按版本号只执行一次；本脚本在手工重跑时若索引已存在会报错，
--       故调用方需按「首次执行」语义使用（与同批其他 DM 迁移一致）。
--
-- 【未实测】按项目既有惯例，达梦方言迁移长期未在真实达梦实例上验证
-- （见 .superpowers-memory/LEARNING_BACKLOG.md）。实际应用前需在达梦实例上先行验证。
-- ============================================================================

EXECUTE IMMEDIATE 'CREATE INDEX IDX_RIRD_REGISTERED_ITEM
    ON MDR.REGISTERED_ITEM_REFERENCE_DOCUMENT (REGISTERED_ITEM_ID)';
/
EXECUTE IMMEDIATE 'CREATE INDEX IDX_RIRD_REFERENCE_DOCUMENT
    ON MDR.REGISTERED_ITEM_REFERENCE_DOCUMENT (REFERENCE_DOCUMENT_ID)';
/
EXECUTE IMMEDIATE 'CREATE INDEX IDX_RISR_REGISTERED_ITEM
    ON MDR.REGISTERED_ITEM_SUBMISSION_RECORD (REGISTERED_ITEM_ID)';
/
EXECUTE IMMEDIATE 'CREATE INDEX IDX_RISR_SUBMISSION_RECORD
    ON MDR.REGISTERED_ITEM_SUBMISSION_RECORD (SUBMISSION_RECORD_ID)';
/
