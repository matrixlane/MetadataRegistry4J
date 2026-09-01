-- 类型修正:Datetime_Period 与 ISO/IEC 11179-3:2023 6.2.3 Datetime 对齐(含时区)。
-- 四张表的 start_date_time/end_date_time 由 datetime 改为 datetimeoffset,
-- 与实体 OffsetDateTime 映射一致(JDBC 无法把 datetime 读成 OffsetDateTime)。
-- 幂等:仅当列当前类型为 datetime 时执行。既有值按 +00:00 偏移保留。

DECLARE @tables TABLE (name SYSNAME);
INSERT INTO @tables VALUES
    ('value_domain'),
    ('permissible_value_meaning'),
    ('permissible_value_set'),
    ('value_meaning_set');

DECLARE @t SYSNAME, @c SYSNAME, @sql NVARCHAR(MAX);
DECLARE table_cur CURSOR FOR SELECT name FROM @tables;
OPEN table_cur;
FETCH NEXT FROM table_cur INTO @t;
WHILE @@FETCH_STATUS = 0
BEGIN
    DECLARE col_cur CURSOR FOR
        SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_NAME = @t
          AND COLUMN_NAME IN ('start_date_time', 'end_date_time')
          AND DATA_TYPE = 'datetime';
    OPEN col_cur;
    FETCH NEXT FROM col_cur INTO @c;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @sql = N'ALTER TABLE ' + QUOTENAME(@t) + N' ALTER COLUMN '
                 + QUOTENAME(@c) + N' datetimeoffset NULL';
        EXEC sp_executesql @sql;
        FETCH NEXT FROM col_cur INTO @c;
    END
    CLOSE col_cur;
    DEALLOCATE col_cur;
    FETCH NEXT FROM table_cur INTO @t;
END
CLOSE table_cur;
DEALLOCATE table_cur;
