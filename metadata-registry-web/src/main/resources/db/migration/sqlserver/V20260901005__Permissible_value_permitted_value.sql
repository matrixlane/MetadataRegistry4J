-- 5.1 PermissibleValue.permitted_value(ISO/IEC 11179-31:2023 表 6 / 7.4.2.12.4,多重性 1)
-- 回填策略:取各允许值的首选指称符号(语言优先 zh-CN > en-US > 其他),与
-- ItemNamingHelper.preferredDesignation 的取值规则一致。
-- 说明:同批次编译看不到新列,后续语句一律动态 SQL 执行。

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'permissible_value' AND COLUMN_NAME = 'permitted_value'
)
BEGIN
    ALTER TABLE mdr.permissible_value ADD permitted_value NVARCHAR(255) NULL;
END;

EXEC(N'
UPDATE pv
SET permitted_value = (
    SELECT TOP 1 d.value
    FROM mdr.designation d
    LEFT JOIN mdr.language_identification li ON d.language_id = li.id
    WHERE d.item_id = pv.id
    ORDER BY CASE li.language_identifier
                 WHEN ''zh-CN'' THEN 0
                 WHEN ''en-US'' THEN 1
                 ELSE 2
             END, d.value
)
FROM mdr.permissible_value pv
WHERE pv.permitted_value IS NULL;
');

-- 无指称可取值的存量行兜底占位(核库时应为 0 行;占位值部署方可后续修订)
EXEC(N'UPDATE mdr.permissible_value SET permitted_value = N'''' WHERE permitted_value IS NULL;');

IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'permissible_value' AND COLUMN_NAME = 'permitted_value'
      AND IS_NULLABLE = 'YES'
)
BEGIN
    EXEC(N'ALTER TABLE mdr.permissible_value ALTER COLUMN permitted_value NVARCHAR(255) NOT NULL;');
END;
