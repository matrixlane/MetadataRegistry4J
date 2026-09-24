-- Acceptability CHECK 与标准小写落库对齐（详见 sqlite 同名脚本头注释）。
-- 达梦:动态 SQL 按 all_cons_columns 发现 acceptability 列上既有 CHECK 约束并替换为
-- 大小写不敏感版本，幂等可重入;待达梦实例实测(KNOWN_FAILURES 同款遗留)。
DECLARE
  v_cnt NUMBER;
  CURSOR c_chk IS
    SELECT acc.constraint_name, acc.table_name
      FROM all_constraints acc
      JOIN all_cons_columns col ON col.owner = acc.owner
                               AND col.constraint_name = acc.constraint_name
     WHERE acc.owner = 'MDR'
       AND acc.constraint_type = 'C'
       AND col.column_name = 'ACCEPTABILITY'
       AND acc.constraint_name NOT LIKE 'CK\_%\_ACCEPTABILITY\_STD' ESCAPE '\'
       AND acc.table_name IN ('DESIGNATION', 'DEFINITION', 'DESIGNATION_CONTEXT_RELATION',
                              'DEFINITION_CONTEXT_RELATION', 'DESIGNATION_DEFINITION_PAIRING');
  TYPE t_tab IS VARRAY(5) OF VARCHAR(64);
  v_tabs t_tab := t_tab('DESIGNATION', 'DEFINITION', 'DESIGNATION_CONTEXT_RELATION',
                        'DEFINITION_CONTEXT_RELATION', 'DESIGNATION_DEFINITION_PAIRING');
BEGIN
  FOR r IN c_chk LOOP
    EXECUTE IMMEDIATE 'ALTER TABLE MDR.' || r.table_name || ' DROP CONSTRAINT ' || r.constraint_name;
  END LOOP;

  FOR i IN 1 .. v_tabs.COUNT LOOP
    SELECT COUNT(*) INTO v_cnt FROM all_constraints
     WHERE owner = 'MDR' AND constraint_name = 'CK_' || v_tabs(i) || '_ACCEPTABILITY_STD';
    IF v_cnt = 0 THEN
      EXECUTE IMMEDIATE 'ALTER TABLE MDR.' || v_tabs(i) ||
        ' ADD CONSTRAINT CK_' || v_tabs(i) || '_ACCEPTABILITY_STD' ||
        ' CHECK (LOWER(ACCEPTABILITY) IN (''preferred'',''admitted'',''deprecated'',''superseded'',''obsolete''))';
    END IF;
  END LOOP;
END;
/
