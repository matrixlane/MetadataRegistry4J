package io.github.matrixlane.metadataregistry.web.notification.service;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationRecord;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;

import java.util.Map;

/**
 * 通知服务抽象接口。
 */
public interface NotificationService {

    NotificationChannel getChannel();

    NotificationRecord send(String recipientId, NotificationType notificationType,
                            String title, String content, String relatedBusinessId);

    NotificationRecord sendWithTemplate(String recipientId, NotificationType notificationType,
                                        Map<String, Object> variables, String relatedBusinessId);

    boolean isEnabled(String recipientId, NotificationType notificationType);
}
