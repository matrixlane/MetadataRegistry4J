-- ============================================================================
-- 通知记录表
-- ============================================================================
CREATE TABLE notification_record (
    id              UNIQUEIDENTIFIER    NOT NULL PRIMARY KEY,
    recipient_id    NVARCHAR(64)        NOT NULL,
    notification_type NVARCHAR(32)      NOT NULL,
    channel         NVARCHAR(16)        NOT NULL,
    title           NVARCHAR(256)       NOT NULL,
    content         NVARCHAR(4000)      NOT NULL,
    status          NVARCHAR(16)        NOT NULL DEFAULT 'PENDING',
    retry_count     INT                 NOT NULL DEFAULT 0,
    max_retries     INT                 NOT NULL DEFAULT 3,
    sent_at         DATETIME2,
    next_retry_at   DATETIME2,
    error_message   NVARCHAR(2000),
    related_business_id NVARCHAR(64),
    created_at      DATETIME2           NOT NULL DEFAULT GETUTCDATE()
);

CREATE INDEX idx_notification_recipient ON notification_record (recipient_id, created_at DESC);
CREATE INDEX idx_notification_retry ON notification_record (status, retry_count, next_retry_at)
    WHERE status = 'PENDING' AND retry_count > 0;
CREATE INDEX idx_notification_business ON notification_record (related_business_id);

-- ============================================================================
-- 通知偏好表
-- ============================================================================
CREATE TABLE notification_preference (
    id                  UNIQUEIDENTIFIER    NOT NULL PRIMARY KEY,
    user_id             NVARCHAR(64)        NOT NULL,
    notification_type   NVARCHAR(32)        NOT NULL,
    channel             NVARCHAR(16)        NOT NULL,
    enabled             BIT                 NOT NULL DEFAULT 1,
    updated_at          DATETIME2
);

CREATE UNIQUE INDEX uk_notification_preference_user_type_channel
    ON notification_preference (user_id, notification_type, channel);
CREATE INDEX idx_notification_preference_user ON notification_preference (user_id);
