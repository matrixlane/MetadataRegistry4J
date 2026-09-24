-- SubmissionRecord 应用扩展属性 source（align-submission-record-to-iso）：
-- 区分工作空间提交（USER_SUBMISSION）与历史脏数据（SYSTEM_DRAFT）。
-- 历史脏数据：迁移前已存在的 SR 行（旧实现"编辑保存即新建"产生）一律标注 SYSTEM_DRAFT。
-- 新代码路径一律显式写 USER_SUBMISSION，SYSTEM_DRAFT 实例不再产生。
-- SQLite 不支持 ALTER TABLE ADD COLUMN IF NOT EXISTS；依赖 Hibernate ddl-auto=update
-- 在 EMF 初始化时添加 source 列（personal profile 默认为 none，但 team/sqlserver
-- 默认 ddl-auto=update 已生效；首次部署也由启动顺序保证）。
-- 本脚本执行 UPDATE 把历史数据标为 SYSTEM_DRAFT；列缺失时 Flyway 会失败，
-- 此时应手动或由 Hibernate ddl-auto 先加列。
UPDATE submission_record SET source = 'SYSTEM_DRAFT' WHERE source IS NULL;