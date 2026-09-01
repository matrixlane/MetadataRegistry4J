-- 类型修正:Datetime_Period 与 ISO/IEC 11179-3:2023 6.2.3 Datetime 对齐(含时区)。
-- SQLite 类型亲和为动态存储,datetime 与 datetimeoffset 均落 TEXT/NUMERIC 亲和列,
-- 无需 ALTER;实体改 OffsetDateTime 后由 Hibernate 按新映射读写,存量 TEXT 值可继续解析。
-- 本脚本为占位,保持三方言迁移版本号一致。
SELECT 1;
