-- 删除 ext.account.active_registrar_id 列（personal-no-login-registrar-default，详见 sqlite 同名脚本）：
-- 列上的默认约束（若 Hibernate ddl-auto 曾生成）先显式删除，否则 5074 依赖错误
-- （先例 V20261008001 真实库实测）。
IF COL_LENGTH('ext.account', 'active_registrar_id') IS NOT NULL
BEGIN
    DECLARE @dropDefaultSql NVARCHAR(MAX);
    SELECT @dropDefaultSql = N'ALTER TABLE ext.account DROP CONSTRAINT ' + QUOTENAME(dc.name)
    FROM sys.default_constraints dc
    JOIN sys.tables t ON t.object_id = dc.parent_object_id
    JOIN sys.schemas s ON s.schema_id = t.schema_id
    JOIN sys.columns c ON c.default_object_id = dc.object_id
    WHERE s.name = 'ext' AND t.name = 'account' AND c.name = 'active_registrar_id';
    IF @dropDefaultSql IS NOT NULL EXEC(@dropDefaultSql);

    EXEC('ALTER TABLE ext.account DROP COLUMN active_registrar_id');
END;
