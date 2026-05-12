package io.github.matrixlane.metadataregistry.web.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "notification_preference",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_preference_user_type_channel",
                columnNames = {"user_id", "notification_type", "channel"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 32)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannel channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static NotificationPreference of(String userId, NotificationType notificationType,
                                            NotificationChannel channel, boolean enabled) {
        NotificationPreference pref = new NotificationPreference();
        pref.setUserId(userId);
        pref.setNotificationType(notificationType);
        pref.setChannel(channel);
        pref.setEnabled(enabled);
        pref.setUpdatedAt(LocalDateTime.now());
        return pref;
    }

    public void toggle(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }
}
