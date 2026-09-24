-- 移除"映射方案"与映射订阅（用户决策 2026-09-20，详见 sqlite 同名脚本头注释）。
-- 幂等：逐对象存在性守卫；先动态删除引用这两表的全部外键
-- （SQL Server 上 Hibernate ddl-auto 会为 @ManyToOne 生成不可预测命名的 FK，
--   实测存在 mdr.item_mapping → ext.mapping_scheme 的 FK，不先删除会报 3726）。
DECLARE @fkName nvarchar(128), @fkSchema nvarchar(128), @fkTable nvarchar(128), @dropSql nvarchar(1000);
DECLARE fkCursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT fk.name, sch.name, t.name
    FROM sys.foreign_keys fk
    JOIN sys.tables t ON fk.parent_object_id = t.object_id
    JOIN sys.schemas sch ON t.schema_id = sch.schema_id
    WHERE fk.referenced_object_id IN (OBJECT_ID('ext.mapping_scheme'), OBJECT_ID('ext.mapping_subscription'));
OPEN fkCursor;
FETCH NEXT FROM fkCursor INTO @fkName, @fkSchema, @fkTable;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @dropSql = N'ALTER TABLE ' + QUOTENAME(@fkSchema) + N'.' + QUOTENAME(@fkTable)
                 + N' DROP CONSTRAINT ' + QUOTENAME(@fkName);
    EXEC sp_executesql @dropSql;
    FETCH NEXT FROM fkCursor INTO @fkName, @fkSchema, @fkTable;
END
CLOSE fkCursor;
DEALLOCATE fkCursor;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='ext' AND TABLE_NAME='mapping_scheme') DROP TABLE ext.mapping_scheme;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='ext' AND TABLE_NAME='mapping_subscription') DROP TABLE ext.mapping_subscription;
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name='idx_fem_scheme' AND object_id=OBJECT_ID('ext.fhir_element_mapping')) DROP INDEX idx_fem_scheme ON ext.fhir_element_mapping;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='item_mapping' AND COLUMN_NAME='mapping_scheme_id') ALTER TABLE mdr.item_mapping DROP COLUMN mapping_scheme_id;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='ext' AND TABLE_NAME='fhir_element_mapping' AND COLUMN_NAME='mapping_scheme_id') ALTER TABLE ext.fhir_element_mapping DROP COLUMN mapping_scheme_id;
