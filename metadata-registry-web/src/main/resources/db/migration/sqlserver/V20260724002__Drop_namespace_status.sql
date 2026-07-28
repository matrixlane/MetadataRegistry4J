
-- 移除 NamespaceStatus 扩展字段，ISO/IEC 11179-3:2023 中 Namespace 生命周期通过 AdministeredItem → RegistrationState 表达。
-- 依赖该列的 CHECK/DEFAULT 约束（如 Hibernate 生成的枚举 CHECK）必须先删除，
-- 否则 DROP COLUMN 会以 “对象依赖于列” 失败。
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('namespace') AND name = 'namespace_status')
BEGIN
    DECLARE @ns_constraint NVARCHAR(128);

    SELECT @ns_constraint = cc.name
    FROM sys.check_constraints cc
    WHERE cc.parent_object_id = OBJECT_ID('namespace')
      AND COL_NAME(cc.parent_object_id, cc.parent_column_id) = 'namespace_status';
    IF @ns_constraint IS NOT NULL
        EXEC('ALTER TABLE namespace DROP CONSTRAINT ' + @ns_constraint);

    SET @ns_constraint = NULL;
    SELECT @ns_constraint = dc.name
    FROM sys.default_constraints dc
    WHERE dc.parent_object_id = OBJECT_ID('namespace')
      AND COL_NAME(dc.parent_object_id, dc.parent_column_id) = 'namespace_status';
    IF @ns_constraint IS NOT NULL
        EXEC('ALTER TABLE namespace DROP CONSTRAINT ' + @ns_constraint);
END

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('namespace') AND name = 'namespace_status')
    EXEC('ALTER TABLE namespace DROP COLUMN namespace_status');
