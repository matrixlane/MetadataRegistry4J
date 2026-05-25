-- WI-028-C: Add Namespace governance columns
ALTER TABLE namespace ADD COLUMN shorthand_prefix VARCHAR(32);
ALTER TABLE namespace ADD COLUMN scheme_reference VARCHAR(255);
ALTER TABLE namespace ADD COLUMN mandatory_naming_convention_indicator BIT DEFAULT 0;
ALTER TABLE namespace ADD COLUMN one_item_per_name_indicator BIT DEFAULT 1;

-- Built-in system namespace marker (e.g. "system" for the built-in system-default namespace)
ALTER TABLE namespace ADD COLUMN naming_authority VARCHAR(128);

-- Create unique constraint on shorthand_prefix for idempotent system-default NS creation
CREATE UNIQUE INDEX uk_namespace_shorthand_prefix ON namespace(shorthand_prefix) WHERE shorthand_prefix IS NOT NULL;