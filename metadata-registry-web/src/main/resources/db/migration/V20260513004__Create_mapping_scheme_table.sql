-- Mapping scheme table for WI-011-2-3-1
-- SQL Server dialect
-- Stores versioned mapping scheme containers with lifecycle status (DRAFT/PUBLISHED/ARCHIVED)

CREATE TABLE mapping_scheme (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    name NVARCHAR(255) NOT NULL,
    source_uri NVARCHAR(2048) NOT NULL,
    target_uri NVARCHAR(2048) NOT NULL,
    description NVARCHAR(4000),
    version NVARCHAR(32) NOT NULL,
    status NVARCHAR(16) NOT NULL,
    parent_id UNIQUEIDENTIFIER,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,

    -- Unique constraint: no duplicate versions within the same lineage
    CONSTRAINT uq_mapping_scheme_parent_version UNIQUE (parent_id, version)
);

CREATE INDEX idx_mapping_scheme_status ON mapping_scheme(status);
CREATE INDEX idx_mapping_scheme_parent ON mapping_scheme(parent_id);
CREATE INDEX idx_mapping_scheme_name ON mapping_scheme(name);
