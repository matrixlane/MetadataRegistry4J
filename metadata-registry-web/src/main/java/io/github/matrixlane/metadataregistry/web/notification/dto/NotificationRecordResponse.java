package io.github.matrixlane.metadataregistry.web.notification.dto;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationStatus;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRecordResponse {

    private UUID id;
    private String recipientId;
    private NotificationType notificationType;
    private NotificationChannel channel;
    private String title;
    private String content;
    private NotificationStatus status;
    private int retryCount;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private String relatedBusinessId;
}
