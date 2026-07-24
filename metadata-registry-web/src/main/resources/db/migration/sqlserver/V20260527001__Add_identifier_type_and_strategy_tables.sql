-- WI-028-B: ScopedIdentifier Type-Driven Generation Strategy
-- Migration for identifier_type field, strategy config table, and sequence registry

-- =============================================================================
-- 1. Create identifier_strategy_config table first (referenced by scoped_identifier FK)
-- =============================================================================
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[identifier_strategy_config]') AND type in (N'U'))
BEGIN
    CREATE TABLE identifier_strategy_config (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        strategy_type           VARCHAR(32) NOT NULL,          -- UUID, URN, SEQUENCE, TEMPLATE
        identifier_type         VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',  -- INTERNAL, URN, OID, LOINC, SNOMED, CUSTOM
        namespace_id            BIGINT,                         -- NULL means global default
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

    CREATE INDEX ix_strategy_config_namespace ON identifier_strategy_config(namespace_id);
    CREATE INDEX ix_strategy_config_type ON identifier_strategy_config(identifier_type);
    CREATE INDEX ix_strategy_config_status ON identifier_strategy_config(status);
    EXEC('CREATE INDEX ix_strategy_config_global ON identifier_strategy_config(is_global_default) WHERE is_global_default = 1');
END;

-- =============================================================================
-- 2. Add columns to scoped_identifier
-- =============================================================================
-- SQL Server idempotent: use EXEC to avoid parsing issues
IF COL_LENGTH('scoped_identifier', 'identifier_type') IS NULL
BEGIN
    EXEC('ALTER TABLE scoped_identifier ADD identifier_type VARCHAR(32) NOT NULL DEFAULT ''INTERNAL''');
    -- Update existing rows to have INTERNAL as default (data migration)
    EXEC('UPDATE scoped_identifier SET identifier_type = ''INTERNAL'' WHERE identifier_type IS NULL');
END;

IF COL_LENGTH('scoped_identifier', 'effective_strategy_id') IS NULL
    EXEC('ALTER TABLE scoped_identifier ADD effective_strategy_id BIGINT');

IF NOT EXISTS (SELECT * FROM sys.foreign_keys WHERE name = 'fk_scoped_identifier_strategy')
    ALTER TABLE scoped_identifier ADD CONSTRAINT fk_scoped_identifier_strategy
        FOREIGN KEY (effective_strategy_id) REFERENCES identifier_strategy_config(id);

-- =============================================================================
-- 3. Create identifier_sequence_registry table (for SequenceStrategy)
-- =============================================================================
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[identifier_sequence_registry]') AND type in (N'U'))
BEGIN
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
END;

-- =============================================================================
-- 4. Insert global default strategy (UUID as fallback)
-- =============================================================================
IF NOT EXISTS (SELECT * FROM identifier_strategy_config WHERE strategy_type = 'UUID' AND identifier_type = 'INTERNAL' AND is_global_default = 1)
BEGIN
    INSERT INTO identifier_strategy_config (strategy_type, identifier_type, namespace_id, config_json, version, status, is_global_default, created_by, created_at, effective_from, sequence_scope)
    VALUES ('UUID', 'INTERNAL', NULL, '{}', 1, 'PUBLISHED', 1, 'system', SYSDATETIME(), SYSDATETIME(), 'PER_NAMESPACE_TYPE');
END;
