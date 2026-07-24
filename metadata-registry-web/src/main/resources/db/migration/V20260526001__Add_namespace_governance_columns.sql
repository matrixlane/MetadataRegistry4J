-- WI-028-C: Add Namespace governance columns
-- SQL Server idempotent: use EXEC with sys.columns check to avoid parsing issues
-- All statements are guarded on dbo.namespace existing: on fresh databases the
-- table is created later by Hibernate (ddl-auto), which already includes these
-- columns. Unqualified references break when a legacy mdr-schema table exists.
IF OBJECT_ID(N'dbo.namespace', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.namespace') AND name = 'shorthand_prefix')
    EXEC('ALTER TABLE dbo.namespace ADD shorthand_prefix VARCHAR(32)');

IF OBJECT_ID(N'dbo.namespace', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.namespace') AND name = 'scheme_reference')
    EXEC('ALTER TABLE dbo.namespace ADD scheme_reference VARCHAR(255)');

IF OBJECT_ID(N'dbo.namespace', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.namespace') AND name = 'mandatory_naming_convention_indicator')
    EXEC('ALTER TABLE dbo.namespace ADD mandatory_naming_convention_indicator BIT DEFAULT 0');

IF OBJECT_ID(N'dbo.namespace', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.namespace') AND name = 'one_item_per_name_indicator')
    EXEC('ALTER TABLE dbo.namespace ADD one_item_per_name_indicator BIT DEFAULT 1');

-- Built-in system namespace marker
IF OBJECT_ID(N'dbo.namespace', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID(N'dbo.namespace') AND name = 'naming_authority')
    EXEC('ALTER TABLE dbo.namespace ADD naming_authority VARCHAR(128)');

-- Create unique constraint if not exists
IF OBJECT_ID(N'dbo.namespace', 'U') IS NOT NULL AND NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'uk_namespace_shorthand_prefix')
    EXEC('CREATE UNIQUE INDEX uk_namespace_shorthand_prefix ON dbo.namespace(shorthand_prefix) WHERE shorthand_prefix IS NOT NULL');
