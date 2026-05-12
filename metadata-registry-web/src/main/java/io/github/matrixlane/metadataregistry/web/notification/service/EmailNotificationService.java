package io.github.matrixlane.metadataregistry.web.notification.service;

import io.github.matrixlane.metadataregistry.web.auth.AppUser;
import io.github.matrixlane.metadataregistry.web.auth.AppUserRepository;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationPreference;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationRecord;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationStatus;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import io.github.matrixlane.metadataregistry.web.notification.repository.NotificationPreferenceRepository;
import io.github.matrixlane.metadataregistry.web.notification.repository.NotificationRecordRepository;
import io.github.matrixlane.metadataregistry.web.notification.template.NotificationTemplateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService implements NotificationService {

    private final JavaMailSender mailSender;
    private final AppUserRepository appUserRepository;
    private final NotificationRecordRepository notificationRecordRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    @Transactional
    public NotificationRecord send(String recipientId, NotificationType notificationType,
                                   String title, String content, String relatedBusinessId) {
        NotificationRecord record = NotificationRecord.of(
                recipientId, notificationType, getChannel(), title, content, relatedBusinessId);

        Optional<String> emailOpt = resolveEmail(recipientId);
        if (emailOpt.isEmpty()) {
            log.debug("用户 {} 未配置邮箱，跳过邮件通知", recipientId);
            record.markSent();
            return notificationRecordRepository.save(record);
        }

        String email = emailOpt.get();
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(email);
            message.setSubject(title);
            message.setText(content);
            mailSender.send(message);
            record.markSent();
            log.debug("邮件通知已发送: recipient={}, email={}, type={}", recipientId, email, notificationType);
        } catch (MailException e) {
            record.markFailed(e.getMessage());
            log.warn("邮件通知发送失败: recipient={}, email={}, error={}", recipientId, email, e.getMessage());
        }

        return notificationRecordRepository.save(record);
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
                .orElse(false);
    }

    private Optional<String> resolveEmail(String recipientId) {
        return appUserRepository.findByUsername(recipientId)
                .map(AppUser::getEmail)
                .filter(e -> !e.isBlank());
    }
}
