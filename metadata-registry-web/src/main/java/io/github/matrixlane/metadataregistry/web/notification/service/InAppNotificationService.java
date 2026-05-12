package io.github.matrixlane.metadataregistry.web.notification.service;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationPreference;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationRecord;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import io.github.matrixlane.metadataregistry.web.notification.repository.NotificationPreferenceRepository;
import io.github.matrixlane.metadataregistry.web.notification.repository.NotificationRecordRepository;
import io.github.matrixlane.metadataregistry.web.notification.template.NotificationTemplateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InAppNotificationService implements NotificationService {

    private final NotificationRecordRepository notificationRecordRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    @Transactional
    public NotificationRecord send(String recipientId, NotificationType notificationType,
                                   String title, String content, String relatedBusinessId) {
        NotificationRecord record = NotificationRecord.of(
                recipientId, notificationType, getChannel(), title, content, relatedBusinessId);
        record.markSent();
        NotificationRecord saved = notificationRecordRepository.save(record);
        log.debug("站内信通知已创建: recipient={}, type={}", recipientId, notificationType);
        return saved;
    }

    @Override
    @Transactional
    public NotificationRecord sendWithTemplate(String recipientId, NotificationType notificationType,
                                               Map<String, Object> variables, String relatedBusinessId) {
        NotificationTemplateType template = NotificationTemplateType.fromNotificationType(notificationType);
        String title = template.renderTitle(variables);
        String content = template.renderContent(variables);
        return send(recipientId, notificationType, title, content, relatedBusinessId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEnabled(String recipientId, NotificationType notificationType) {
        return preferenceRepository
                .findByUserIdAndNotificationTypeAndChannel(recipientId, notificationType, getChannel())
                .map(NotificationPreference::isEnabled)
                .orElse(true);
    }
}
