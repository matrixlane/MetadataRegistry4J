-- Align Namespace entity with ISO/IEC 11179-3:2023 model.
-- Idempotent for SQL Server.

-- 1. Add missing governance columns
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('namespace') AND name = 'one_name_per_item_indicator')
    EXEC('ALTER TABLE namespace ADD one_name_per_item_indicator BIT DEFAULT 0');

-- 2. Add new foreign keys for naming_authority (Organization) and registration_authority (RegistrationAuthority)
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('namespace') AND name = 'naming_authority_id')
    EXEC('ALTER TABLE namespace ADD naming_authority_id UNIQUEIDENTIFIER');

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('namespace') AND name = 'registration_authority_id')
    EXEC('ALTER TABLE namespace ADD registration_authority_id UNIQUEIDENTIFIER');

-- 3. Drop the legacy plain-text naming_authority column
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('namespace') AND name = 'naming_authority')
    EXEC('ALTER TABLE namespace DROP COLUMN naming_authority');

-- 4. Drop the legacy naming_convention_id column (replaced by join table)
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('namespace') AND name = 'naming_convention_id')
    EXEC('ALTER TABLE namespace DROP COLUMN naming_convention_id');

-- 5. Join table: namespace <-> naming_convention (ISO 11179-3 8.6.6 naming_convention_utilization)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'namespace_naming_convention')
    EXEC('CREATE TABLE namespace_naming_convention (
            namespace_id UNIQUEIDENTIFIER NOT NULL,
            naming_convention_id UNIQUEIDENTIFIER NOT NULL,
            PRIMARY KEY (namespace_id, naming_convention_id)
        )');

-- 6. Join table: designation <-> naming_convention (ISO 11179-3 8.6.5 naming_convention_conformance)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'designation_naming_convention')
    EXEC('CREATE TABLE designation_naming_convention (
            designation_id UNIQUEIDENTIFIER NOT NULL,
            naming_convention_id UNIQUEIDENTIFIER NOT NULL,
            PRIMARY KEY (designation_id, naming_convention_id)
        )');
