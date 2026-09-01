-- 3.1 Contact.account 扩展关联剥为卫星实体(ISO/IEC 11179 标准实体只留标准属性)
-- 建表 + 从 mdr.contact 回填 + 删除旧列。
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'contact_account_binding'
)
BEGIN
    CREATE TABLE mdr.contact_account_binding (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        contact_id UNIQUEIDENTIFIER NOT NULL,
        account_id UNIQUEIDENTIFIER NOT NULL
    );
    CREATE UNIQUE INDEX uk_cab_contact ON mdr.contact_account_binding (contact_id);
    CREATE INDEX idx_cab_account ON mdr.contact_account_binding (account_id);
END;

IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'contact' AND COLUMN_NAME = 'account_id'
)
BEGIN
    EXEC(N'INSERT INTO mdr.contact_account_binding (id, contact_id, account_id)
    SELECT NEWID(), id, account_id
    FROM mdr.contact
    WHERE account_id IS NOT NULL');

    -- 删 FK 约束(若存在)后再删列
    DECLARE @fk NVARCHAR(128);
    SELECT @fk = fk.name
    FROM sys.foreign_keys fk
    JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
    JOIN sys.tables t ON fkc.parent_object_id = t.object_id
    JOIN sys.schemas sch ON t.schema_id = sch.schema_id
    JOIN sys.columns c ON fkc.parent_object_id = c.object_id AND fkc.parent_column_id = c.column_id
    WHERE sch.name = 'mdr' AND t.name = 'contact' AND c.name = 'account_id';
    IF @fk IS NOT NULL
        EXEC(N'ALTER TABLE mdr.contact DROP CONSTRAINT ' + @fk);

    ALTER TABLE mdr.contact DROP COLUMN account_id;
END;
