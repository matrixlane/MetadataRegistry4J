package io.github.matrixlane.metadataregistry.web.notification.service;

import io.github.matrixlane.metadataregistry.web.auth.AppUser;
import io.github.matrixlane.metadataregistry.web.auth.AppUserRepository;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationRecord;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationStatus;
import io.github.matrixlane.metadataregistry.web.notification.repository.NotificationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRetryService {

    private final NotificationRecordRepository notificationRecordRepository;
    private final JavaMailSender mailSender;
    private final AppUserRepository appUserRepository;

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void retryPendingNotifications() {
        List<NotificationRecord> pendingRecords = notificationRecordRepository.findPendingRetries(LocalDateTime.now());
        if (pendingRecords.isEmpty()) {
            return;
        }

        log.info("发现 {} 条待重试通知", pendingRecords.size());
        for (NotificationRecord record : pendingRecords) {
            if (record.getChannel() == NotificationChannel.EMAIL) {
                retryEmail(record);
            } else {
                record.markSent();
                notificationRecordRepository.save(record);
            }
        }
    }

    private void retryEmail(NotificationRecord record) {
        if (!record.canRetry()) {
            log.warn("邮件通知已达最大重试次数: recordId={}, recipient={}", record.getId(), record.getRecipientId());
            record.setStatus(NotificationStatus.FAILED);
            notificationRecordRepository.save(record);
            return;
        }

        Optional<String> emailOpt = resolveEmail(record.getRecipientId());
        if (emailOpt.isEmpty()) {
            log.warn("用户 {} 未配置邮箱，放弃邮件重试", record.getRecipientId());
            record.markFailed("用户未配置邮箱");
            notificationRecordRepository.save(record);
            return;
        }

        String email = emailOpt.get();
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject(record.getTitle());
            message.setText(record.getContent());
            mailSender.send(message);
            record.markSent();
            log.info("邮件通知重试成功: recordId={}, recipient={}", record.getId(), record.getRecipientId());
        } catch (MailException e) {
            record.markFailed(e.getMessage());
            log.warn("邮件通知重试失败: recordId={}, recipient={}, retryCount={}",
                    record.getId(), record.getRecipientId(), record.getRetryCount());
        }

        notificationRecordRepository.save(record);
    }

    private Optional<String> resolveEmail(String recipientId) {
        return appUserRepository.findByUsername(recipientId)
                .map(AppUser::getEmail)
                .filter(e -> !e.isBlank());
    }
}
