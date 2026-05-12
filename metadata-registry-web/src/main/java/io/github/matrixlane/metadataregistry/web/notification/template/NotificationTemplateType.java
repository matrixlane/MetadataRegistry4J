package io.github.matrixlane.metadataregistry.web.notification.template;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;

import java.util.Map;

/**
 * 通知模板集中式管理枚举。
 */
public enum NotificationTemplateType implements NotificationTemplate {

    APPROVAL_SUBMITTED(
            NotificationType.APPROVAL_SUBMITTED,
            "【元数据审批】有新的待审批任务：{itemName}",
            "您好，{approverName}：\n\n"
                    + "提交人 {submitterName} 提交了元数据项「{itemName}」，需要您的审批。\n"
                    + "审批意见：{opinion}\n\n"
                    + "请及时登录系统处理。",
            false
    ),

    APPROVAL_APPROVED(
            NotificationType.APPROVAL_APPROVED,
            "【元数据审批】您的提交已通过审批：{itemName}",
            "您好，{submitterName}：\n\n"
                    + "您提交的元数据项「{itemName}」已通过审批。\n"
                    + "审批人：{approverName}\n"
                    + "审批意见：{opinion}\n\n"
                    + "该元数据项已自动发布为标准状态。",
            false
    ),

    APPROVAL_REJECTED(
            NotificationType.APPROVAL_REJECTED,
            "【元数据审批】您的提交已被退回：{itemName}",
            "您好，{submitterName}：\n\n"
                    + "您提交的元数据项「{itemName}」已被退回。\n"
                    + "审批人：{approverName}\n"
                    + "退回意见：{opinion}\n\n"
                    + "请修改后重新提交。",
            false
    ),

    APPROVAL_TIMEOUT(
            NotificationType.APPROVAL_TIMEOUT,
            "【元数据审批】审批任务已超时：{itemName}",
            "您好，{approverName}：\n\n"
                    + "元数据项「{itemName}」的审批任务已超时。\n"
                    + "提交人：{submitterName}\n\n"
                    + "请尽快登录系统处理。",
            false
    );

    private final NotificationType notificationType;
    private final String titleTemplate;
    private final String contentTemplate;
    private final boolean html;

    NotificationTemplateType(NotificationType notificationType, String titleTemplate,
                             String contentTemplate, boolean html) {
        this.notificationType = notificationType;
        this.titleTemplate = titleTemplate;
        this.contentTemplate = contentTemplate;
        this.html = html;
    }

    @Override
    public NotificationType getNotificationType() {
        return notificationType;
    }

    @Override
    public String renderTitle(Map<String, Object> variables) {
        return render(titleTemplate, variables);
    }

    @Override
    public String renderContent(Map<String, Object> variables) {
        return render(contentTemplate, variables);
    }

    @Override
    public boolean isHtml() {
        return html;
    }

    public static NotificationTemplateType fromNotificationType(NotificationType type) {
        for (NotificationTemplateType template : values()) {
            if (template.notificationType == type) {
                return template;
            }
        }
        throw new IllegalArgumentException("未找到通知类型对应的模板: " + type);
    }

    private String render(String template, Map<String, Object> variables) {
        if (variables == null) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue().toString();
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }
}
