-- 2.4 Scoped_Identifier 扩展属性剥为卫星实体(ISO/IEC 11179 标准实体只留标准属性)
-- 建表 + 从 mdr.scoped_identifier 回填 + 删除 7 个旧列。
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'scoped_identifier_lifecycle'
)
BEGIN
    CREATE TABLE mdr.scoped_identifier_lifecycle (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        scoped_identifier_id UNIQUEIDENTIFIER NOT NULL,
        identifier_creation_mode NVARCHAR(16) NULL,
        version_creation_mode NVARCHAR(16) NULL,
        version_status NVARCHAR(16) NULL,
        created_at DATETIME2 NULL,
        published_at DATETIME2 NULL,
        deprecated_at DATETIME2 NULL,
        obsolete_at DATETIME2 NULL
    );
    CREATE UNIQUE INDEX uk_sil_scoped_identifier
        ON mdr.scoped_identifier_lifecycle (scoped_identifier_id);
END;

IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'scoped_identifier' AND COLUMN_NAME = 'version_status'
)
BEGIN
    EXEC(N'INSERT INTO mdr.scoped_identifier_lifecycle
        (id, scoped_identifier_id, identifier_creation_mode, version_creation_mode,
         version_status, created_at, published_at, deprecated_at, obsolete_at)
    SELECT NEWID(), id, identifier_creation_mode, version_creation_mode,
           version_status, created_at, published_at, deprecated_at, obsolete_at
    FROM mdr.scoped_identifier');

    ALTER TABLE mdr.scoped_identifier DROP COLUMN
        identifier_creation_mode, version_creation_mode, version_status,
        created_at, published_at, deprecated_at, obsolete_at;
END;
