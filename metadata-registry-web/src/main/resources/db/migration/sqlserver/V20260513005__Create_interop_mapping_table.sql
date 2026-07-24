-- Interoperability mapping result table for WI-013-2a
-- SQL Server dialect
-- Stores match results produced by the matching algorithm engine

CREATE TABLE interop_mapping (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    interop_data_element_id UNIQUEIDENTIFIER NOT NULL,
    local_item_id UNIQUEIDENTIFIER NOT NULL,
    confidence_score DECIMAL(3,2) NOT NULL,
    strategy NVARCHAR(50) NOT NULL,
    status NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason NVARCHAR(1000),
    confirmed_by UNIQUEIDENTIFIER,
    confirmed_at DATETIME2,
    created_at DATETIME2 NOT NULL DEFAULT GETDATE(),

    -- Prevent duplicate mappings for the same interop+local pair
    CONSTRAINT uq_interop_mapping_pair UNIQUE (interop_data_element_id, local_item_id),

    -- Status must be one of the valid enum values
    CONSTRAINT chk_interop_mapping_status CHECK (
        status IN ('AUTO_MATCHED', 'PENDING', 'CONFIRMED', 'REJECTED')
    ),

    -- Confidence score must be in [0.00, 1.00]
    CONSTRAINT chk_interop_mapping_score CHECK (
        confidence_score >= 0.00 AND confidence_score <= 1.00
    )
);

-- Indexes for common query patterns
CREATE INDEX idx_interop_mapping_interop_id ON interop_mapping(interop_data_element_id);
CREATE INDEX idx_interop_mapping_local_id ON interop_mapping(local_item_id);
CREATE INDEX idx_interop_mapping_status ON interop_mapping(status);
CREATE INDEX idx_interop_mapping_strategy ON interop_mapping(strategy);
CREATE INDEX idx_interop_mapping_confidence ON interop_mapping(confidence_score DESC);
