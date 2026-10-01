-- 清除 MDR.REGISTRAR 的非标准列 IDENTIFIER，回归 ISO/IEC 11179-3:2023 §6.3.5：
-- Contact 的标准属性仅有 organization / individual / role 三个关联（+ id），
-- Registrar 子类只额外挂 registration_authority_id；IDENTIFIER 是应用曾自行添加的属性，
-- 标准无此语义，本次删除。
--
-- 存量数据（形如 {username}-registrar 的自动生成值）随列删除一并丢失：
-- 该列无标准语义、无检索/展示依赖，可接受。
-- 达梦方言：PL/SQL 块守卫，列存在性查 ALL_TAB_COLUMNS，DDL 走 EXECUTE IMMEDIATE；
-- 与 SQL Server 版本语义一致。迁移不可重复执行（Flyway 一次语义），守卫仅为跨环境兜底。
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM ALL_TAB_COLUMNS WHERE OWNER='MDR' AND TABLE_NAME='REGISTRAR' AND COLUMN_NAME='IDENTIFIER';
  IF n = 1 THEN EXECUTE IMMEDIATE 'ALTER TABLE MDR.REGISTRAR DROP COLUMN IDENTIFIER'; END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
