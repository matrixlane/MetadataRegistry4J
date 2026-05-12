package io.github.matrixlane.metadataregistry.web.notification.event;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import lombok.Getter;

import java.util.Map;

/**
 * 通知事件。由业务模块发布，通知系统监听并异步处理投递。
 */
@Getter
public class NotificationEvent {

    private final String recipientId;
    private final NotificationType notificationType;
    private final Map<String, Object> variables;
    private final String relatedBusinessId;

    public NotificationEvent(String recipientId, NotificationType notificationType,
                             Map<String, Object> variables, String relatedBusinessId) {
        this.recipientId = recipientId;
        this.notificationType = notificationType;
        this.variables = variables;
        this.relatedBusinessId = relatedBusinessId;
    }

    public static NotificationEvent of(String recipientId, NotificationType notificationType,
                                       Map<String, Object> variables, String relatedBusinessId) {
        return new NotificationEvent(recipientId, notificationType, variables, relatedBusinessId);
    }
}
