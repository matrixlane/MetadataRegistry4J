package io.github.matrixlane.metadataregistry.web.notification.dto;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceResponse {

    private UUID id;
    private String userId;
    private NotificationType notificationType;
    private NotificationChannel channel;
    private boolean enabled;
}
