-- Dimensionality.coordinate_indicator 放宽为可空:
-- 三态 Boolean——TRUE=坐标测量、FALSE=区间测量、
-- NULL=非物理单位(Not applicable)。审计 #7(2026-09-01)已将实体改为 Boolean;
-- 既有库由 Hibernate ddl-auto 建表,早期为 BIT NOT NULL,存量列需同步放宽。
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='mdr'
    AND TABLE_NAME='dimensionality' AND COLUMN_NAME='coordinate_indicator' AND IS_NULLABLE='NO')
    ALTER TABLE mdr.dimensionality ALTER COLUMN coordinate_indicator BIT NULL;
