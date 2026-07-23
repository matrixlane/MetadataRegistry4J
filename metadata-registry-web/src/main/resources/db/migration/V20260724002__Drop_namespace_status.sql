
-- 移除 NamespaceStatus 扩展字段，ISO/IEC 11179-3:2023 中 Namespace 生命周期通过 AdministeredItem → RegistrationState 表达。
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('namespace') AND name = 'namespace_status')
    EXEC('ALTER TABLE namespace DROP COLUMN namespace_status');
