package io.github.matrixlane.metadataregistry.web.notification.repository;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationPreference;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    List<NotificationPreference> findByUserId(String userId);

    Optional<NotificationPreference> findByUserIdAndNotificationTypeAndChannel(
            String userId, NotificationType notificationType, NotificationChannel channel);

    List<NotificationPreference> findByUserIdAndChannel(String userId, NotificationChannel channel);
}
