-- WI-028-C: Add Namespace governance columns
-- SQL Server idempotent: use EXEC to avoid parsing issues in Flyway batches
IF COL_LENGTH('namespace', 'shorthand_prefix') IS NULL
    EXEC('ALTER TABLE namespace ADD shorthand_prefix VARCHAR(32)');

IF COL_LENGTH('namespace', 'scheme_reference') IS NULL
    EXEC('ALTER TABLE namespace ADD scheme_reference VARCHAR(255)');

IF COL_LENGTH('namespace', 'mandatory_naming_convention_indicator') IS NULL
    EXEC('ALTER TABLE namespace ADD mandatory_naming_convention_indicator BIT DEFAULT 0');

IF COL_LENGTH('namespace', 'one_item_per_name_indicator') IS NULL
    EXEC('ALTER TABLE namespace ADD one_item_per_name_indicator BIT DEFAULT 1');

-- Built-in system namespace marker
IF COL_LENGTH('namespace', 'naming_authority') IS NULL
    EXEC('ALTER TABLE namespace ADD naming_authority VARCHAR(128)');

-- Create unique constraint if not exists
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'uk_namespace_shorthand_prefix')
    CREATE UNIQUE INDEX uk_namespace_shorthand_prefix ON namespace(shorthand_prefix) WHERE shorthand_prefix IS NOT NULL;