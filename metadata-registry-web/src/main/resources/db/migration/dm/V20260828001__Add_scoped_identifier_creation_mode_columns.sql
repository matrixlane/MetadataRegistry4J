-- 2026-08-28-persist-item-creation-mode
-- 达梦 (Oracle-compatible) 迁移：使用 EXECUTE IMMEDIATE + 异常吞噬保证幂等。

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE scoped_identifier ADD identifier_creation_mode VARCHAR2(16)';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE scoped_identifier ADD version_creation_mode VARCHAR2(16)';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/