-- REGISTRATION_AUTHORITY 新增 IS_PRIMARY 标志（authority-contact-governance，详见 sqlite 同名脚本头注释）：
-- 存量行回填 false；主要机构由启动调和按 id 字典序最小补标（幂等）。
-- 达梦：动态 SQL 守卫探测列存在性（ALL_TAB_COLUMNS），DDL 走 EXECUTE IMMEDIATE；
-- 与 SQL Server 版本语义一致。待达梦实例实测（项目惯例）。
DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM all_tab_columns
   WHERE owner = 'MDR'
     AND table_name = 'REGISTRATION_AUTHORITY'
     AND column_name = 'IS_PRIMARY';
  IF v_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE MDR.REGISTRATION_AUTHORITY ADD IS_PRIMARY BIT DEFAULT 0 NOT NULL';
  END IF;
END;
/
