-- 5.6 value_domain_meaning 提升到 ValueDomain 基类(ISO/IEC 11179-31:2023 7.2.3.1 / 7.4.4.1)
-- value_domain 增加 conceptual_domain_id,回填自三个子类表的旧 FK 列,随后删除旧列。
-- 子类型一致约束(§7.4.5.2.1)由服务层 ValueDomainConceptualDomains 强制,不落库级约束。
-- 说明:同批次编译看不到新列,涉及新列的语句一律动态 SQL 执行。

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'value_domain' AND COLUMN_NAME = 'conceptual_domain_id'
)
BEGIN
    ALTER TABLE mdr.value_domain ADD conceptual_domain_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE mdr.value_domain ADD CONSTRAINT fk_value_domain_conceptual_domain
        FOREIGN KEY (conceptual_domain_id) REFERENCES mdr.conceptual_domain(id);
END;

EXEC(N'
UPDATE vd
SET conceptual_domain_id = dvd.described_conceptual_domain_id
FROM mdr.value_domain vd
JOIN mdr.described_value_domain dvd ON vd.id = dvd.id
WHERE vd.conceptual_domain_id IS NULL AND dvd.described_conceptual_domain_id IS NOT NULL;
');

EXEC(N'
UPDATE vd
SET conceptual_domain_id = levd.local_enumerated_conceptual_domain_id
FROM mdr.value_domain vd
JOIN mdr.local_enumerated_value_domain levd ON vd.id = levd.id
WHERE vd.conceptual_domain_id IS NULL AND levd.local_enumerated_conceptual_domain_id IS NOT NULL;
');

EXEC(N'
UPDATE vd
SET conceptual_domain_id = revd.referenced_conceptual_domain_id
FROM mdr.value_domain vd
JOIN mdr.reference_enumerated_value_domain revd ON vd.id = revd.id
WHERE vd.conceptual_domain_id IS NULL AND revd.referenced_conceptual_domain_id IS NOT NULL;
');

-- 删除三个子类表旧 FK 列(先动态定位并删除其外键约束)
DECLARE @drop NVARCHAR(MAX) = N'';
SELECT @drop = @drop + N'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + N'.' + QUOTENAME(t.name)
    + N' DROP CONSTRAINT ' + QUOTENAME(fk.name) + N';'
FROM sys.foreign_keys fk
JOIN sys.tables t ON fk.parent_object_id = t.object_id
JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
JOIN sys.columns c ON fkc.parent_object_id = c.object_id AND fkc.parent_column_id = c.column_id
WHERE SCHEMA_NAME(t.schema_id) = 'mdr'
  AND ((t.name = 'described_value_domain' AND c.name = 'described_conceptual_domain_id')
    OR (t.name = 'local_enumerated_value_domain' AND c.name = 'local_enumerated_conceptual_domain_id')
    OR (t.name = 'reference_enumerated_value_domain' AND c.name = 'referenced_conceptual_domain_id'));
IF @drop <> N'' EXEC sp_executesql @drop;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='mdr'
    AND TABLE_NAME='described_value_domain' AND COLUMN_NAME='described_conceptual_domain_id')
    ALTER TABLE mdr.described_value_domain DROP COLUMN described_conceptual_domain_id;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='mdr'
    AND TABLE_NAME='local_enumerated_value_domain' AND COLUMN_NAME='local_enumerated_conceptual_domain_id')
    ALTER TABLE mdr.local_enumerated_value_domain DROP COLUMN local_enumerated_conceptual_domain_id;

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='mdr'
    AND TABLE_NAME='reference_enumerated_value_domain' AND COLUMN_NAME='referenced_conceptual_domain_id')
    ALTER TABLE mdr.reference_enumerated_value_domain DROP COLUMN referenced_conceptual_domain_id;
