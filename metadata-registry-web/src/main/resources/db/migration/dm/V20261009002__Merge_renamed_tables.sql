-- ============================================================================
-- V20261009002__Merge_renamed_tables.sql  (达梦)
--
-- mdr-standard-compliance-cleanup 补充：与 SQL Server 版同语义——ddl-auto 先于 Flyway
-- 时新名表已被预建，把旧名表数据按行去重并入新名表后删旧表；新名缺失则更名。
-- 全部计数守卫。
-- ============================================================================

DECLARE
  old_count NUMBER;
  new_count NUMBER;
  col_list VARCHAR2(4000);
  pred VARCHAR2(8000);
  sep_cols VARCHAR2(2);
  sep_pred VARCHAR2(5);
BEGIN
  FOR r IN (SELECT 'CONCEPTUAL_DOMAIN_DEC' o, 'DATA_ELEMENT_CONCEPT_DOMAIN' n FROM DUAL UNION ALL
            SELECT 'DESIGNATION_CONTEXT_RELATION', 'DESIGNATION_CONTEXT' FROM DUAL UNION ALL
            SELECT 'DEFINITION_CONTEXT_RELATION', 'DEFINITION_CONTEXT' FROM DUAL UNION ALL
            SELECT 'ITEM_MAPPING_SUBJECT', 'SUBJECT_MAPPING' FROM DUAL UNION ALL
            SELECT 'ITEM_MAPPING_OBJECT', 'OBJECT_MAPPING' FROM DUAL UNION ALL
            SELECT 'DESIGNATION_NAMING_CONVENTION', 'NAMING_CONVENTION_CONFORMANCE' FROM DUAL UNION ALL
            SELECT 'NAMESPACE_NAMING_CONVENTION', 'NAMING_CONVENTION_UTILIZATION' FROM DUAL UNION ALL
            SELECT 'UNIT_OF_MEASURE_MEASURE_CLASS', 'UNIT_OF_MEASURE_CLASS' FROM DUAL UNION ALL
            SELECT 'SUBMISSION_RECORD_REGISTERED_ITEM', 'REGISTERED_ITEM_SUBMISSION_RECORD' FROM DUAL)
  LOOP
    SELECT COUNT(*) INTO old_count FROM ALL_TABLES
      WHERE OWNER = 'MDR' AND TABLE_NAME = r.o;
    IF old_count = 1 THEN
      SELECT COUNT(*) INTO new_count FROM ALL_TABLES
        WHERE OWNER = 'MDR' AND TABLE_NAME = r.n;
      IF new_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE MDR.' || r.o || ' RENAME TO ' || r.n;
      ELSE
        -- 公共列 + 逐列 null-safe 等值去重（ROWID 跨表无意义,不可用）
        col_list := ''; pred := ''; sep_cols := ''; sep_pred := '';
        FOR c IN (SELECT COLUMN_NAME FROM ALL_TAB_COLUMNS
                    WHERE OWNER = 'MDR' AND TABLE_NAME = r.o
                  INTERSECT
                  SELECT COLUMN_NAME FROM ALL_TAB_COLUMNS
                    WHERE OWNER = 'MDR' AND TABLE_NAME = r.n
                  ORDER BY COLUMN_NAME)
        LOOP
          col_list := col_list || sep_cols || c.COLUMN_NAME;
          pred := pred || sep_pred || '(n.' || c.COLUMN_NAME || ' = o.' || c.COLUMN_NAME
               || ' OR (n.' || c.COLUMN_NAME || ' IS NULL AND o.' || c.COLUMN_NAME || ' IS NULL))';
          sep_cols := ', ';
          sep_pred := ' AND ';
        END LOOP;
        IF col_list IS NOT NULL THEN
          EXECUTE IMMEDIATE 'INSERT INTO MDR.' || r.n || ' (' || col_list || ')'
              || ' SELECT ' || col_list || ' FROM MDR.' || r.o || ' o WHERE NOT EXISTS'
              || ' (SELECT 1 FROM MDR.' || r.n || ' n WHERE ' || pred || ')';
        END IF;
        EXECUTE IMMEDIATE 'DROP TABLE MDR.' || r.o || ' CASCADE CONSTRAINTS';
      END IF;
    END IF;
  END LOOP;
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
