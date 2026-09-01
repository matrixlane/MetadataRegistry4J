-- 类型修正:DataTypeScheme.reference → scheme_reference Reference_Document(11179-31:2023 表 11)。
-- 达梦(Oracle 兼容),幂等靠异常吞噬。

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE data_type_scheme ADD scheme_reference_id CHAR(36) NULL';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE data_type_scheme DROP COLUMN reference';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/
