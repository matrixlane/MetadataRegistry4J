-- ============================================================================
-- V20261023001__Drop_mdr_registration_transition_orphans.sql  (达梦)
--
-- 与 SQL Server 版同批次。达梦以用户即模式，registration_transition_* 三表本就
-- 与应用同处、不存在 mdr/ext 双副本孤儿问题，本迁移为空转（遗留任务与
-- V20261009005 达梦分支一致）。
-- ============================================================================

DECLARE
  noop NUMBER;
BEGIN
  SELECT 0 INTO noop FROM DUAL;
END;
/
