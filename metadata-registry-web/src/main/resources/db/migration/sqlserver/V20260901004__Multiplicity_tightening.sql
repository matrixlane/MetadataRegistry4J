-- 多重性修正(ISO/IEC 11179-3:2023 / 11179-31:2023):必填列收紧 NOT NULL。
-- 核库结果:除 registration_authority.international_code_designator 1 行 NULL 外全部 0 NULL。
-- 幂等:动态读取列当前类型与可空性,仅当可空时 ALTER;类型按现状保留。

-- 1) 回填:registration_authority.international_code_designator(表 8 必备,1 行存量 NULL)
--    占位值 '0000':ISO/IEC 6523 未分配段,部署方应改为本机构真实 ICD。
UPDATE mdr.registration_authority
SET international_code_designator = '0000'
WHERE international_code_designator IS NULL;

-- 2) 动态收紧以下列为 NOT NULL(仅当当前可空)
DECLARE @tighten TABLE (tbl SYSNAME, col SYSNAME);
INSERT INTO @tighten VALUES
    ('value_domain', 'data_type_id'),                    -- 11179-31 表 5:datatype 1
    ('scoped_identifier', 'version'),                    -- 11179-3 表 12:version 1
    ('registration_state', 'registration_status'),       -- 表 22:registration_status 1
    ('registration_state', 'effective_date'),            -- 表 22:effective_date 1
    ('administered_item', 'creation_date_time'),         -- 表 21:creation_datetime 1
    ('stewardship_record', 'organization_id'),           -- 表 26:organization 1
    ('stewardship_record', 'contact_id'),                -- 表 26:contact 1
    ('submission_record', 'organization_id'),            -- 表 27:organization 1
    ('submission_record', 'contact_id'),                 -- 表 27:contact 1
    ('registration_authority', 'international_code_designator'), -- 表 8:1
    ('registration_authority', 'organization_identifier'),       -- 表 8:1
    ('registry_specification', 'name'),                  -- 表 28:name 1
    ('registry_specification', 'character_repertoire');  -- 表 28:character_repertoire 1

DECLARE @tbl SYSNAME, @col SYSNAME, @sql NVARCHAR(MAX);
DECLARE cur CURSOR FOR
    SELECT c.TABLE_NAME, c.COLUMN_NAME
    FROM INFORMATION_SCHEMA.COLUMNS c
    JOIN @tighten t ON t.tbl = c.TABLE_NAME AND t.col = c.COLUMN_NAME
    WHERE c.TABLE_SCHEMA = 'mdr' AND c.IS_NULLABLE = 'YES';
OPEN cur;
FETCH NEXT FROM cur INTO @tbl, @col;
WHILE @@FETCH_STATUS = 0
BEGIN
    DECLARE @type NVARCHAR(128);
    SELECT @type = DATA_TYPE +
        CASE
            WHEN CHARACTER_MAXIMUM_LENGTH > 0 THEN '(' + CAST(CHARACTER_MAXIMUM_LENGTH AS VARCHAR) + ')'
            WHEN DATA_TYPE IN ('datetime2', 'datetimeoffset') AND DATETIME_PRECISION IS NOT NULL
                THEN '(' + CAST(DATETIME_PRECISION AS VARCHAR) + ')'
            ELSE ''
        END
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = @tbl AND COLUMN_NAME = @col;
    SET @sql = N'ALTER TABLE mdr.' + QUOTENAME(@tbl) + N' ALTER COLUMN '
             + QUOTENAME(@col) + N' ' + @type + N' NOT NULL';
    EXEC sp_executesql @sql;
    FETCH NEXT FROM cur INTO @tbl, @col;
END
CLOSE cur;
DEALLOCATE cur;
