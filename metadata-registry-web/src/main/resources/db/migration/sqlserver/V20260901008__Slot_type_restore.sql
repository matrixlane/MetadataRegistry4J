-- 1.9 Slot 恢复标准属性名 type(ISO/IEC 11179-3:2023 表 11:type 0..1 String)
-- 双字段并存期建的表同时有 type 与 value_datatype:回填后删除废弃列;
-- 若表只有 value_datatype(实体一度只映射它),直接重命名。
IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'slot' AND COLUMN_NAME = 'value_datatype'
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'slot' AND COLUMN_NAME = 'type'
    )
    BEGIN
        EXEC(N'UPDATE mdr.slot SET type = value_datatype WHERE type IS NULL AND value_datatype IS NOT NULL;');
        ALTER TABLE mdr.slot DROP COLUMN value_datatype;
    END
    ELSE
    BEGIN
        EXEC(N'EXEC sp_rename ''mdr.slot.value_datatype'', ''type'', ''COLUMN'';');
    END
END;
