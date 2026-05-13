package io.github.matrixlane.metadataregistry.web.notification.entity;

/**
 * 通知业务类型枚举。
 */
public enum NotificationType {
    APPROVAL_SUBMITTED("审批提交", "有新的元数据项待您审批"),
    APPROVAL_APPROVED("审批通过", "您提交的元数据项已通过审批"),
    APPROVAL_REJECTED("审批退回", "您提交的元数据项已被退回"),
    APPROVAL_TIMEOUT("审批超时", "您的审批任务已超时"),
    MAPPING_CHANGED("映射变更", "您订阅的映射关系发生了变更");

    private final String displayName;
    private final String defaultTitle;

    NotificationType(String displayName, String defaultTitle) {
        this.displayName = displayName;
        this.defaultTitle = defaultTitle;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }
}
