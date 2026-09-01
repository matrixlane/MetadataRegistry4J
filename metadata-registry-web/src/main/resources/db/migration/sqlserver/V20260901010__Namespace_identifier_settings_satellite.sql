-- 2.3 Namespace 标识符治理扩展属性剥为卫星实体(ISO/IEC 11179 标准实体只留标准属性)
-- 建表 + 从 mdr.namespace 回填 + 删除 4 个旧列。
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'namespace_identifier_settings'
)
BEGIN
    CREATE TABLE mdr.namespace_identifier_settings (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        namespace_id UNIQUEIDENTIFIER NOT NULL,
        versioning_policy NVARCHAR(32) NULL,
        identifier_input_mode NVARCHAR(32) NULL,
        version_input_mode NVARCHAR(32) NULL,
        manual_identifier_pattern NVARCHAR(255) NULL
    );
    CREATE UNIQUE INDEX uk_nis_namespace
        ON mdr.namespace_identifier_settings (namespace_id);
END;

IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'namespace' AND COLUMN_NAME = 'versioning_policy'
)
BEGIN
    EXEC(N'INSERT INTO mdr.namespace_identifier_settings
        (id, namespace_id, versioning_policy, identifier_input_mode,
         version_input_mode, manual_identifier_pattern)
    SELECT NEWID(), id, versioning_policy, identifier_input_mode,
           version_input_mode, manual_identifier_pattern
    FROM mdr.namespace');

    ALTER TABLE mdr.namespace DROP COLUMN
        versioning_policy, identifier_input_mode, version_input_mode, manual_identifier_pattern;
END;
