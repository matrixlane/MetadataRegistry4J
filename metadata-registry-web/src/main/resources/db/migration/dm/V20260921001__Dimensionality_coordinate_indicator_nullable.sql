-- Dimensionality.coordinate_indicator 放宽为可空(详见 sqlite 同名脚本头注释;
-- 三态 Boolean,NULL=Not applicable)。
-- 达梦:动态 SQL 守卫,幂等;待达梦实例实测(KNOWN_FAILURES 同款遗留)。
DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM all_tab_columns
   WHERE owner = 'MDR'
     AND table_name = 'DIMENSIONALITY'
     AND column_name = 'COORDINATE_INDICATOR'
     AND nullable = 'N';
  IF v_count > 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE MDR.DIMENSIONALITY MODIFY COORDINATE_INDICATOR BIT NULL';
  END IF;
END;
/
