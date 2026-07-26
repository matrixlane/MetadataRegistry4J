-- cleanup-identifier-type-and-simplify-strategy-routing
-- Dameng (Oracle-compatible) cleanup migration.

-- 1. scoped_identifier: drop FK, effective_strategy_id column, identifier_type column
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE scoped_identifier DROP CONSTRAINT fk_scoped_identifier_strategy';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE scoped_identifier DROP COLUMN effective_strategy_id';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE scoped_identifier DROP COLUMN identifier_type';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- 2. identifier_strategy_config: drop type column and old unique constraint, add namespace+version unique constraint
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE identifier_strategy_config DROP CONSTRAINT uk_strategy_namespace_type';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE identifier_strategy_config DROP COLUMN identifier_type';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE identifier_strategy_config DROP COLUMN sequence_scope';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE identifier_strategy_config ADD CONSTRAINT uk_strategy_namespace UNIQUE (namespace_id, version)';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- 3. identifier_sequence_registry: drop type column and old unique constraint, add namespace unique constraint
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE identifier_sequence_registry DROP CONSTRAINT uk_seq_registry_namespace_type';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE identifier_sequence_registry DROP COLUMN identifier_type';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE identifier_sequence_registry ADD CONSTRAINT uk_seq_registry_namespace UNIQUE (namespace_id)';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/
