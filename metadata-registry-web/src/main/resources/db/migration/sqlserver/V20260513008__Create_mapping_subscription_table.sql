-- Mapping subscription table for WI-011-3: Item_Mapping Quality & Impact
-- SQL Server dialect
-- Stores user subscriptions to mapping schemes and individual items

CREATE TABLE mapping_subscription (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    user_id NVARCHAR(64) NOT NULL,
    subscription_type NVARCHAR(16) NOT NULL,
    target_id UNIQUEIDENTIFIER NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT GETDATE(),

    -- Ensure a user can only subscribe once to the same target
    CONSTRAINT uq_mapping_subscription_user_type_target UNIQUE (user_id, subscription_type, target_id),

    -- Validate subscription type values
    CONSTRAINT chk_mapping_subscription_type CHECK (
        subscription_type IN ('SCHEME', 'ITEM')
    )
);

CREATE INDEX idx_mapping_subscription_user ON mapping_subscription(user_id);
CREATE INDEX idx_mapping_subscription_target ON mapping_subscription(subscription_type, target_id);
GO
