-- cleanup-identifier-type-and-simplify-strategy-routing
-- Remove IdentifierType dimension from scoped_identifier, identifier_strategy_config, identifier_sequence_registry
-- and drop the now-unused effective_strategy_id FK on scoped_identifier.

-- 1. scoped_identifier: drop FK, effective_strategy_id column, identifier_type column
IF EXISTS (SELECT * FROM sys.foreign_keys WHERE name = 'fk_scoped_identifier_strategy')
    ALTER TABLE scoped_identifier DROP CONSTRAINT fk_scoped_identifier_strategy;

IF COL_LENGTH('scoped_identifier', 'effective_strategy_id') IS NOT NULL
    EXEC('ALTER TABLE scoped_identifier DROP COLUMN effective_strategy_id');

IF COL_LENGTH('scoped_identifier', 'identifier_type') IS NOT NULL
    EXEC('ALTER TABLE scoped_identifier DROP COLUMN identifier_type');

-- 2. identifier_strategy_config: drop type column and old unique constraint, add namespace+version unique constraint
IF EXISTS (SELECT * FROM sys.indexes WHERE name = 'uk_strategy_namespace_type' AND object_id = OBJECT_ID('identifier_strategy_config'))
    ALTER TABLE identifier_strategy_config DROP CONSTRAINT uk_strategy_namespace_type;

IF COL_LENGTH('identifier_strategy_config', 'identifier_type') IS NOT NULL
    EXEC('ALTER TABLE identifier_strategy_config DROP COLUMN identifier_type');

IF COL_LENGTH('identifier_strategy_config', 'sequence_scope') IS NOT NULL
    EXEC('ALTER TABLE identifier_strategy_config DROP COLUMN sequence_scope');

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'uk_strategy_namespace' AND object_id = OBJECT_ID('identifier_strategy_config'))
    ALTER TABLE identifier_strategy_config ADD CONSTRAINT uk_strategy_namespace UNIQUE (namespace_id, version);

-- 3. identifier_sequence_registry: drop type column and old unique constraint, add namespace unique constraint
IF EXISTS (SELECT * FROM sys.indexes WHERE name = 'uk_seq_registry_namespace_type' AND object_id = OBJECT_ID('identifier_sequence_registry'))
    ALTER TABLE identifier_sequence_registry DROP CONSTRAINT uk_seq_registry_namespace_type;

IF COL_LENGTH('identifier_sequence_registry', 'identifier_type') IS NOT NULL
    EXEC('ALTER TABLE identifier_sequence_registry DROP COLUMN identifier_type');

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'uk_seq_registry_namespace' AND object_id = OBJECT_ID('identifier_sequence_registry'))
    ALTER TABLE identifier_sequence_registry ADD CONSTRAINT uk_seq_registry_namespace UNIQUE (namespace_id);
