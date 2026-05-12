package io.github.matrixlane.metadataregistry.web.notification.dto;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceRequest {

    @NotNull
    private NotificationType notificationType;

    @NotNull
    private NotificationChannel channel;

    private boolean enabled;
}
