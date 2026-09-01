-- 1.5 补标准缺失属性(5.2/5.3/5.4)的库结构部分:
--   conceptual_domain.valid_period(11179-31 表 2,多重性 1)+ dimensionality(0..1)
--   enumerated_conceptual_domain.expanded_on_datetime / locked_datetime(表 3,0..1)
--   data_type.annotation(表 8,0..1)
-- valid_period 回填策略:start_date_time 取 administered_item.creation_date_time
-- (标准允许注册机构自行决定语义,取"在注册系统内生效时间"),无管理项兜底当前时间。

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='mdr'
    AND TABLE_NAME='conceptual_domain' AND COLUMN_NAME='start_date_time')
    ALTER TABLE mdr.conceptual_domain ADD start_date_time DATETIMEOFFSET NULL;

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='mdr'
    AND TABLE_NAME='conceptual_domain' AND COLUMN_NAME='end_date_time')
    ALTER TABLE mdr.conceptual_domain ADD end_date_time DATETIMEOFFSET NULL;

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='mdr'
    AND TABLE_NAME='conceptual_domain' AND COLUMN_NAME='dimensionality_id')
BEGIN
    ALTER TABLE mdr.conceptual_domain ADD dimensionality_id UNIQUEIDENTIFIER NULL;
    ALTER TABLE mdr.conceptual_domain ADD CONSTRAINT fk_conceptual_domain_dimensionality
        FOREIGN KEY (dimensionality_id) REFERENCES mdr.dimensionality(id);
END;

EXEC(N'
UPDATE cd
SET start_date_time = ai.creation_date_time
FROM mdr.conceptual_domain cd
JOIN mdr.identified_item ii ON ii.item_id = cd.id
JOIN mdr.administered_item ai ON ai.id = ii.id
WHERE cd.start_date_time IS NULL AND cd.end_date_time IS NULL;
');

EXEC(N'UPDATE mdr.conceptual_domain SET start_date_time = SYSDATETIMEOFFSET() WHERE start_date_time IS NULL AND end_date_time IS NULL;');

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='mdr'
    AND TABLE_NAME='enumerated_conceptual_domain' AND COLUMN_NAME='expanded_on_datetime')
    ALTER TABLE mdr.enumerated_conceptual_domain ADD expanded_on_datetime DATETIMEOFFSET NULL;

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='mdr'
    AND TABLE_NAME='enumerated_conceptual_domain' AND COLUMN_NAME='locked_datetime')
    ALTER TABLE mdr.enumerated_conceptual_domain ADD locked_datetime DATETIMEOFFSET NULL;

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='mdr'
    AND TABLE_NAME='data_type' AND COLUMN_NAME='annotation')
    ALTER TABLE mdr.data_type ADD annotation NVARCHAR(2000) NULL;
