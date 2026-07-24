-- Alter interop_mapping table for WI-013-2b: 匹配结果管理
-- SQL Server dialect

-- Add dataset_id column for dataset-level filtering
ALTER TABLE interop_mapping
    ADD dataset_id UNIQUEIDENTIFIER NULL;
GO

-- Add version column for optimistic locking
ALTER TABLE interop_mapping
    ADD version BIGINT NOT NULL DEFAULT 0;
GO

-- Add batch_operation_id for undoing batch operations
ALTER TABLE interop_mapping
    ADD batch_operation_id UNIQUEIDENTIFIER NULL;
GO

-- Add previous_status for undo operations
ALTER TABLE interop_mapping
    ADD previous_status NVARCHAR(20) NULL;
GO

-- Add previous_local_item_id for undoing manual remaps
ALTER TABLE interop_mapping
    ADD previous_local_item_id UNIQUEIDENTIFIER NULL;
GO

-- Update status check constraint to include UNMATCHED
ALTER TABLE interop_mapping
    DROP CONSTRAINT chk_interop_mapping_status;
GO

ALTER TABLE interop_mapping
    ADD CONSTRAINT chk_interop_mapping_status CHECK (
        status IN ('AUTO_MATCHED', 'PENDING', 'CONFIRMED', 'REJECTED', 'UNMATCHED')
    );
GO

-- Add check constraint for previous_status
ALTER TABLE interop_mapping
    ADD CONSTRAINT chk_interop_mapping_previous_status CHECK (
        previous_status IS NULL OR previous_status IN ('AUTO_MATCHED', 'PENDING', 'CONFIRMED', 'REJECTED', 'UNMATCHED')
    );
GO

-- Add composite index for dataset + status + confidence (recommended by trio review)
CREATE INDEX idx_interop_mapping_dataset_status_confidence
    ON interop_mapping(dataset_id, status, confidence_score DESC);
GO

-- Add index for batch operation queries
CREATE INDEX idx_interop_mapping_batch_op ON interop_mapping(batch_operation_id);
GO

-- Backfill dataset_id for existing rows (if any)
-- In production this should be populated based on the actual dataset relationship
-- For MVP/development, leave as NULL or update via data migration script
