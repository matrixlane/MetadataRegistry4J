-- WI-028-C: Add Namespace governance columns
-- SQL Server idempotent: use EXEC with sys.columns check to avoid parsing issues
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('namespace') AND name = 'shorthand_prefix')
    EXEC('ALTER TABLE namespace ADD shorthand_prefix VARCHAR(32)');

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('namespace') AND name = 'scheme_reference')
    EXEC('ALTER TABLE namespace ADD scheme_reference VARCHAR(255)');

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('namespace') AND name = 'mandatory_naming_convention_indicator')
    EXEC('ALTER TABLE namespace ADD mandatory_naming_convention_indicator BIT DEFAULT 0');

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('namespace') AND name = 'one_item_per_name_indicator')
    EXEC('ALTER TABLE namespace ADD one_item_per_name_indicator BIT DEFAULT 1');

-- Built-in system namespace marker
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('namespace') AND name = 'naming_authority')
    EXEC('ALTER TABLE namespace ADD naming_authority VARCHAR(128)');

-- Create unique constraint if not exists
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'uk_namespace_shorthand_prefix')
    EXEC('CREATE UNIQUE INDEX uk_namespace_shorthand_prefix ON namespace(shorthand_prefix) WHERE shorthand_prefix IS NOT NULL');