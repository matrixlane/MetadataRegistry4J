-- 3.3 NamingConvention.rulesJson 扩展属性剥为卫星实体(ISO/IEC 11179 标准实体只留标准属性)
-- 建表 + 从 mdr.naming_convention 回填 + 删除旧列。
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'naming_convention_rules'
)
BEGIN
    CREATE TABLE mdr.naming_convention_rules (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        naming_convention_id UNIQUEIDENTIFIER NOT NULL,
        rules_json NVARCHAR(MAX) NULL
    );
    CREATE UNIQUE INDEX uk_ncr_convention ON mdr.naming_convention_rules (naming_convention_id);
END;

IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'naming_convention' AND COLUMN_NAME = 'rules_json'
)
BEGIN
    EXEC(N'INSERT INTO mdr.naming_convention_rules (id, naming_convention_id, rules_json)
    SELECT NEWID(), id, rules_json
    FROM mdr.naming_convention
    WHERE rules_json IS NOT NULL');

    ALTER TABLE mdr.naming_convention DROP COLUMN rules_json;
END;
