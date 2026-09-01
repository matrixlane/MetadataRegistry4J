-- 3.2 LanguageIdentification.enabled 扩展属性剥为卫星实体(ISO/IEC 11179 标准实体只留标准属性)
-- 建表 + 从 mdr.language_identification 回填 + 删除旧列。
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'language_governance'
)
BEGIN
    CREATE TABLE mdr.language_governance (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        language_id UNIQUEIDENTIFIER NOT NULL,
        enabled BIT NOT NULL DEFAULT 1
    );
    CREATE UNIQUE INDEX uk_lg_language ON mdr.language_governance (language_id);
END;

IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'language_identification' AND COLUMN_NAME = 'enabled'
)
BEGIN
    EXEC(N'INSERT INTO mdr.language_governance (id, language_id, enabled)
    SELECT NEWID(), id, ISNULL(enabled, 1)
    FROM mdr.language_identification');

    -- 删默认约束(若存在)后再删列
    DECLARE @df NVARCHAR(128);
    SELECT @df = dc.name
    FROM sys.default_constraints dc
    JOIN sys.tables t ON dc.parent_object_id = t.object_id
    JOIN sys.schemas sch ON t.schema_id = sch.schema_id
    JOIN sys.columns c ON dc.parent_object_id = c.object_id AND dc.parent_column_id = c.column_id
    WHERE sch.name = 'mdr' AND t.name = 'language_identification' AND c.name = 'enabled';
    IF @df IS NOT NULL
        EXEC(N'ALTER TABLE mdr.language_identification DROP CONSTRAINT ' + @df);

    ALTER TABLE mdr.language_identification DROP COLUMN enabled;
END;
