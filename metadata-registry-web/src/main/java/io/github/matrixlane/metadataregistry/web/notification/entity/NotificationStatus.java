package io.github.matrixlane.metadataregistry.web.notification.entity;

/**
 * 通知记录状态枚举。
 */
public enum NotificationStatus {
    PENDING("待发送"),
    SENT("已发送"),
    FAILED("发送失败");

    private final String displayName;

    NotificationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
