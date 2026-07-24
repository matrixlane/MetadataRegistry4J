-- WI-028-B: ScopedIdentifier Type-Driven Generation Strategy
-- Migration for identifier_type field, strategy config table, and sequence registry
--
-- Schema-qualification notes:
-- * All statements target dbo explicitly; unqualified references break when
--   legacy mdr-schema tables with the same names exist.
-- * ALTER/UPDATE statements on scoped_identifier are guarded on the table
--   existing: on fresh databases Hibernate (ddl-auto) creates it later with
--   these columns already present.
-- * namespace_id columns are UNIQUEIDENTIFIER to match Namespace's UUID PK
--   (the entities map namespace_id via @JoinColumn to Namespace).

-- =============================================================================
-- 1. Add identifier_type column to scoped_identifier
-- =============================================================================
-- SQL Server idempotent: use EXEC to avoid parsing issues
IF OBJECT_ID(N'dbo.scoped_identifier', 'U') IS NOT NULL AND COL_LENGTH(N'dbo.scoped_identifier', 'identifier_type') IS NULL
    EXEC('ALTER TABLE dbo.scoped_identifier ADD identifier_type VARCHAR(32) NOT NULL DEFAULT ''INTERNAL''');

-- Update existing rows to have INTERNAL as default (data migration)
IF OBJECT_ID(N'dbo.scoped_identifier', 'U') IS NOT NULL AND COL_LENGTH(N'dbo.scoped_identifier', 'identifier_type') IS NOT NULL
    UPDATE dbo.scoped_identifier SET identifier_type = 'INTERNAL' WHERE identifier_type IS NULL;

-- Add effective_strategy_id column for Immutable guarantee (strategy snapshot)
IF OBJECT_ID(N'dbo.scoped_identifier', 'U') IS NOT NULL AND COL_LENGTH(N'dbo.scoped_identifier', 'effective_strategy_id') IS NULL
    EXEC('ALTER TABLE dbo.scoped_identifier ADD effective_strategy_id BIGINT');

-- =============================================================================
-- 2. Create identifier_strategy_config table
-- =============================================================================
IF OBJECT_ID(N'dbo.identifier_strategy_config', 'U') IS NULL
CREATE TABLE identifier_strategy_config (
    id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
    strategy_type           VARCHAR(32) NOT NULL,          -- UUID, URN, SEQUENCE, TEMPLATE
    identifier_type         VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',  -- INTERNAL, URN, OID, LOINC, SNOMED, CUSTOM
    namespace_id            UNIQUEIDENTIFIER,               -- NULL means global default
    config_json             TEXT,                           -- Strategy-specific JSON config
    version                 INT NOT NULL DEFAULT 1,         -- Version number for immutability
    status                  VARCHAR(16) NOT NULL DEFAULT 'DRAFT',  -- DRAFT, PUBLISHED, DISABLED
    is_global_default       BIT NOT NULL DEFAULT 0,         -- TRUE if this is the global default
    sequence_scope         VARCHAR(32) NOT NULL DEFAULT 'PER_NAMESPACE_TYPE',  -- PER_NAMESPACE, PER_NAMESPACE_TYPE
    effective_from          DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    effective_until         DATETIME2,                      -- NULL means currently effective
    created_by             VARCHAR(128),
    created_at             DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    modified_by            VARCHAR(128),
    modified_at            DATETIME2,
    CONSTRAINT uk_strategy_namespace_type UNIQUE (namespace_id, identifier_type, version)
);

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'ix_strategy_config_namespace')
    CREATE INDEX ix_strategy_config_namespace ON dbo.identifier_strategy_config(namespace_id);
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'ix_strategy_config_type')
    CREATE INDEX ix_strategy_config_type ON dbo.identifier_strategy_config(identifier_type);
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'ix_strategy_config_status')
    CREATE INDEX ix_strategy_config_status ON dbo.identifier_strategy_config(status);
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'ix_strategy_config_global')
    CREATE INDEX ix_strategy_config_global ON dbo.identifier_strategy_config(is_global_default) WHERE is_global_default = 1;

-- =============================================================================
-- 3. Create identifier_sequence_registry table (for SequenceStrategy)
-- =============================================================================
IF OBJECT_ID(N'dbo.identifier_sequence_registry', 'U') IS NULL
CREATE TABLE identifier_sequence_registry (
    id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
    namespace_id            UNIQUEIDENTIFIER NOT NULL,
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

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'ix_seq_registry_namespace')
    CREATE INDEX ix_seq_registry_namespace ON dbo.identifier_sequence_registry(namespace_id);

-- =============================================================================
-- 3b. FK from scoped_identifier to identifier_strategy_config
--     Created only when both tables exist (table order fixed: the referenced
--     table must exist before the FK can be declared).
-- =============================================================================
IF OBJECT_ID(N'dbo.scoped_identifier', 'U') IS NOT NULL
   AND OBJECT_ID(N'dbo.identifier_strategy_config', 'U') IS NOT NULL
   AND COL_LENGTH(N'dbo.scoped_identifier', 'effective_strategy_id') IS NOT NULL
   AND NOT EXISTS (SELECT * FROM sys.foreign_keys WHERE name = 'fk_scoped_identifier_strategy')
    ALTER TABLE dbo.scoped_identifier ADD CONSTRAINT fk_scoped_identifier_strategy
        FOREIGN KEY (effective_strategy_id) REFERENCES dbo.identifier_strategy_config(id);

-- =============================================================================
-- 4. Insert global default strategy (UUID as fallback)
-- =============================================================================
IF NOT EXISTS (SELECT 1 FROM dbo.identifier_strategy_config WHERE is_global_default = 1 AND strategy_type = 'UUID' AND identifier_type = 'INTERNAL')
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
