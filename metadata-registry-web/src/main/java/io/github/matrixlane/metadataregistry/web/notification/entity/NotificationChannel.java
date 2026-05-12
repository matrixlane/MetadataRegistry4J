package io.github.matrixlane.metadataregistry.web.notification.entity;

/**
 * 通知渠道枚举。
 */
public enum NotificationChannel {
    IN_APP("站内信"),
    EMAIL("邮件");

    private final String displayName;

    NotificationChannel(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
