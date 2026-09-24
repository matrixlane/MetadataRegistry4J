-- SubmissionRecord 应用扩展属性 source（align-submission-record-to-iso）：
-- 区分工作空间提交（USER_SUBMISSION）与历史脏数据（SYSTEM_DRAFT）。
-- 历史脏数据：迁移前已存在的 SR 行（旧实现"编辑保存即新建"产生）一律标注 SYSTEM_DRAFT。
-- 新代码路径一律显式写 USER_SUBMISSION，SYSTEM_DRAFT 实例不再产生。
-- Hibernate ddl-auto=update 在应用启动时已可能提前加列，加存在性守卫避免重复列错误。
IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('mdr.submission_record') AND name = 'source')
    ALTER TABLE mdr.submission_record ADD source nvarchar(32);
UPDATE mdr.submission_record SET source = 'SYSTEM_DRAFT' WHERE source IS NULL;