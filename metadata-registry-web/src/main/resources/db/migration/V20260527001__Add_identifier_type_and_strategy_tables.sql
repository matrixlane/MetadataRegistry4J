-- WI-028-B: ScopedIdentifier Type-Driven Generation Strategy
-- Migration for identifier_type field, strategy config table, and sequence registry

-- =============================================================================
-- 1. Add identifier_type column to scoped_identifier
-- =============================================================================
ALTER TABLE scoped_identifier ADD COLUMN identifier_type VARCHAR(32) NOT NULL DEFAULT 'INTERNAL';

-- Update existing rows to have INTERNAL as default (data migration)
UPDATE scoped_identifier SET identifier_type = 'INTERNAL' WHERE identifier_type IS NULL;

-- Add effective_strategy_id column for Immutable guarantee (strategy snapshot)
ALTER TABLE scoped_identifier ADD COLUMN effective_strategy_id BIGINT;

ALTER TABLE scoped_identifier ADD CONSTRAINT fk_scoped_identifier_strategy
    FOREIGN KEY (effective_strategy_id) REFERENCES identifier_strategy_config(id);

-- =============================================================================
-- 2. Create identifier_strategy_config table
-- =============================================================================
CREATE TABLE identifier_strategy_config (
    id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
    strategy_type           VARCHAR(32) NOT NULL,          -- UUID, URN, SEQUENCE, TEMPLATE
    identifier_type         VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',  -- INTERNAL, URN, OID, LOINC, SNOMED, CUSTOM
    namespace_id            BIGINT,                         -- NULL means global default
    config_json             TEXT,                           -- Strategy-specific JSON config
    version                 INT NOT NULL DEFAULT 1,         -- Version number for immutability
    status                  VARCHAR(16) NOT NULL DEFAULT 'DRAFT',  -- DRAFT, PUBLISHED, DISABLED
    is_global_default       BIT NOT NULL DEFAULT 0,         -- TRUE if this is the global default
    sequence_scope         VARCHAR(16) NOT NULL DEFAULT 'PER_NAMESPACE_TYPE',  -- PER_NAMESPACE, PER_NAMESPACE_TYPE
    effective_from          DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    effective_until         DATETIME2,                      -- NULL means currently effective
    created_by             VARCHAR(128),
    created_at             DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    modified_by            VARCHAR(128),
    modified_at            DATETIME2,
    CONSTRAINT uk_strategy_namespace_type UNIQUE (namespace_id, identifier_type, version)
);

CREATE INDEX ix_strategy_config_namespace ON identifier_strategy_config(namespace_id);
CREATE INDEX ix_strategy_config_type ON identifier_strategy_config(identifier_type);
CREATE INDEX ix_strategy_config_status ON identifier_strategy_config(status);
CREATE INDEX ix_strategy_config_global ON identifier_strategy_config(is_global_default) WHERE is_global_default = 1;

-- =============================================================================
-- 3. Create identifier_sequence_registry table (for SequenceStrategy)
-- =============================================================================
CREATE TABLE identifier_sequence_registry (
    id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
    namespace_id            BIGINT NOT NULL,
    identifier_type         VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
    current_value           BIGINT NOT NULL DEFAULT 0,
    increment_by            INT NOT NULL DEFAULT 1,
    start_value             BIGINT NOT NULL DEFAULT 1,
    pad_length              INT NOT NULL DEFAULT 6,         -- Zero-padding length
    prefix                  VARCHAR(32),                   -- Optional prefix (e.g. "DE")
    last_allocated_at       DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    created_by              VARCHAR(128),
    created_at              DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    modified_by             VARCHAR(128),
    modified_at             DATETIME2,
    CONSTRAINT uk_seq_registry_namespace_type UNIQUE (namespace_id, identifier_type)
);

CREATE INDEX ix_seq_registry_namespace ON identifier_sequence_registry(namespace_id);

-- =============================================================================
-- 4. Insert global default strategy (UUID as fallback)
-- =============================================================================
INSERT INTO identifier_strategy_config (strategy_type, identifier_type, namespace_id, config_json, version, status, is_global_default, created_by, created_at)
VALUES ('UUID', 'INTERNAL', NULL, '{}', 1, 'PUBLISHED', 1, 'system', SYSDATETIME());

-- =============================================================================
-- 5. Drop the old unique constraint on (namespace_id, identifier) if it exists
--    and replace with (namespace_id, identifier, identifier_type) uniqueness
--    Note: We allow same identifier value with different types in same namespace
-- =============================================================================
-- The existing uk_scoped_identifier_ns_identifier is kept as-is since
-- (identifier, namespace_id, identifier_type) uniqueness is handled at JPA level
-- by the combination of uniqueConstraint on identifier+namespace (existing)
-- and the business logic allowing multiple types for same item.
-- Per OQ-028-B-1 resolution: identifierType does NOT participate in uniqueness;
-- same item in same namespace can have type=urn and type=oid with different identifiers.
