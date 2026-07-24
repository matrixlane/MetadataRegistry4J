CREATE TABLE version_history (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    item_id UNIQUEIDENTIFIER NOT NULL,
    item_type NVARCHAR(64) NOT NULL,
    version_number INT NOT NULL,
    change_type NVARCHAR(16) NOT NULL,
    changed_by NVARCHAR(128) NOT NULL,
    changed_at DATETIME2 NOT NULL DEFAULT GETDATE(),
    snapshot_json NVARCHAR(MAX) NOT NULL,
    change_summary NVARCHAR(512),
    CONSTRAINT uk_version_history_item_version UNIQUE (item_id, version_number)
);

CREATE INDEX idx_version_history_item ON version_history(item_id);
CREATE INDEX idx_version_history_item_type ON version_history(item_type);
CREATE INDEX idx_version_history_changed_at ON version_history(changed_at DESC);
