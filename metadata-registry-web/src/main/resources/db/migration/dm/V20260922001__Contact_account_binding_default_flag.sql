-- contact_account_binding 增加 is_default 标志(详见 sqlite 同名脚本头注释)。
-- 达梦:动态 SQL 守卫,幂等;待达梦实例实测(KNOWN_FAILURES 同款遗留)。
DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM all_tab_columns
   WHERE owner = 'EXT'
     AND table_name = 'CONTACT_ACCOUNT_BINDING'
     AND column_name = 'IS_DEFAULT';
  IF v_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE EXT.CONTACT_ACCOUNT_BINDING ADD IS_DEFAULT BIT DEFAULT 0 NOT NULL';
  END IF;
END;
/
