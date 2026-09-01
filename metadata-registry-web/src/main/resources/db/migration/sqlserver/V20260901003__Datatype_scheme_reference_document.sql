-- 类型修正:DataTypeScheme.reference(String) 改为 scheme_reference 0..1 Reference_Document
-- (ISO/IEC 11179-31:2023 表 11)。旧文本列 reference 无映射目标,直接删除并新建外键列。
-- 幂等:仅在新列不存在时添加、旧列存在时删除。

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'data_type_scheme' AND COLUMN_NAME = 'scheme_reference_id'
)
BEGIN
    ALTER TABLE data_type_scheme ADD scheme_reference_id UNIQUEIDENTIFIER NULL;
END

IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'data_type_scheme' AND COLUMN_NAME = 'reference'
)
BEGIN
    ALTER TABLE data_type_scheme DROP COLUMN reference;
END
