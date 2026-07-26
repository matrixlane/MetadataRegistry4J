-- cleanup-identifier-type-and-simplify-strategy-routing
-- SQLite 3.35+ supports ALTER TABLE DROP COLUMN.

PRAGMA foreign_keys = OFF;

-- 1. scoped_identifier
ALTER TABLE scoped_identifier DROP COLUMN IF EXISTS identifier_type;
ALTER TABLE scoped_identifier DROP COLUMN IF EXISTS effective_strategy_id;

-- 2. identifier_strategy_config
ALTER TABLE identifier_strategy_config DROP COLUMN IF EXISTS identifier_type;
ALTER TABLE identifier_strategy_config DROP COLUMN IF EXISTS sequence_scope;
DROP INDEX IF EXISTS uk_strategy_namespace_type;
CREATE UNIQUE INDEX IF NOT EXISTS uk_strategy_namespace ON identifier_strategy_config(namespace_id, version);

-- 3. identifier_sequence_registry
ALTER TABLE identifier_sequence_registry DROP COLUMN IF EXISTS identifier_type;
DROP INDEX IF EXISTS uk_seq_registry_namespace_type;
CREATE UNIQUE INDEX IF NOT EXISTS uk_seq_registry_namespace ON identifier_sequence_registry(namespace_id);

PRAGMA foreign_keys = ON;
