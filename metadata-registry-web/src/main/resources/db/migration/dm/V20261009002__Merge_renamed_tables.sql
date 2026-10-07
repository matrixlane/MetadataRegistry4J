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
BEGIN
  FOR r IN (SELECT 'CONCEPTUAL_DOMAIN_DEC' o, 'DATA_ELEMENT_CONCEPT_DOMAIN' n, 0 keyed FROM DUAL UNION ALL
            SELECT 'DESIGNATION_CONTEXT_RELATION', 'DESIGNATION_CONTEXT', 1 FROM DUAL UNION ALL
            SELECT 'DEFINITION_CONTEXT_RELATION', 'DEFINITION_CONTEXT', 1 FROM DUAL UNION ALL
            SELECT 'ITEM_MAPPING_SUBJECT', 'SUBJECT_MAPPING', 1 FROM DUAL UNION ALL
            SELECT 'ITEM_MAPPING_OBJECT', 'OBJECT_MAPPING', 1 FROM DUAL UNION ALL
            SELECT 'DESIGNATION_NAMING_CONVENTION', 'NAMING_CONVENTION_CONFORMANCE', 0 FROM DUAL UNION ALL
            SELECT 'NAMESPACE_NAMING_CONVENTION', 'NAMING_CONVENTION_UTILIZATION', 0 FROM DUAL UNION ALL
            SELECT 'UNIT_OF_MEASURE_MEASURE_CLASS', 'UNIT_OF_MEASURE_CLASS', 0 FROM DUAL UNION ALL
            SELECT 'SUBMISSION_RECORD_REGISTERED_ITEM', 'REGISTERED_ITEM_SUBMISSION_RECORD', 0 FROM DUAL)
  LOOP
    SELECT COUNT(*) INTO old_count FROM ALL_TABLES
      WHERE OWNER = 'MDR' AND TABLE_NAME = r.o;
    IF old_count = 1 THEN
      SELECT COUNT(*) INTO new_count FROM ALL_TABLES
        WHERE OWNER = 'MDR' AND TABLE_NAME = r.n;
      IF new_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE MDR.' || r.o || ' RENAME TO ' || r.n;
      ELSE
        IF r.keyed = 1 THEN
          EXECUTE IMMEDIATE 'INSERT INTO MDR.' || r.n
              || ' SELECT o.* FROM MDR.' || r.o || ' o WHERE NOT EXISTS'
              || ' (SELECT 1 FROM MDR.' || r.n || ' n WHERE n.ID = o.ID)';
        ELSE
          EXECUTE IMMEDIATE 'INSERT INTO MDR.' || r.n
              || ' SELECT o.* FROM MDR.' || r.o || ' o WHERE NOT EXISTS'
              || ' (SELECT 1 FROM MDR.' || r.n || ' n WHERE n.ROWID = o.ROWID)';
        END IF;
        EXECUTE IMMEDIATE 'DROP TABLE MDR.' || r.o || ' CASCADE CONSTRAINTS';
      END IF;
    END IF;
  END LOOP;
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
