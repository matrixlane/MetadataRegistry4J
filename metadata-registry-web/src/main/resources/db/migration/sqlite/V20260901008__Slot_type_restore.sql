-- 1.9 Slot 恢复标准属性名 type(ISO/IEC 11179-3:2023 表 11:type 0..1 String)
-- 基线 slot 表双字段并存(type 与 value_datatype),回填后删除废弃列。
UPDATE slot SET type = value_datatype WHERE type IS NULL AND value_datatype IS NOT NULL;
ALTER TABLE slot DROP COLUMN value_datatype;
