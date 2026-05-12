package io.github.matrixlane.metadataregistry.web.notification.service;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationRecord;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationStatus;
import io.github.matrixlane.metadataregistry.web.notification.repository.NotificationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationRecordRepository notificationRecordRepository;

    public Page<NotificationRecord> getInbox(String recipientId, Pageable pageable) {
        return notificationRecordRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
    }

    public List<NotificationRecord> getUnreadNotifications(String recipientId) {
        return notificationRecordRepository.findUnreadByRecipient(
                recipientId, NotificationChannel.IN_APP,
                List.of(NotificationStatus.PENDING, NotificationStatus.SENT)
        );
    }

    public long countUnread(String recipientId) {
        return notificationRecordRepository.countByRecipientIdAndChannelAndStatusIn(
                recipientId, NotificationChannel.IN_APP,
                List.of(NotificationStatus.PENDING, NotificationStatus.SENT)
        );
    }

    @Transactional
    public NotificationRecord markAsRead(UUID notificationId) {
        NotificationRecord record = notificationRecordRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在: " + notificationId));
        if (record.getStatus() == NotificationStatus.PENDING) {
            record.markSent();
            notificationRecordRepository.save(record);
        }
        return record;
    }

    public NotificationRecord getNotification(UUID notificationId) {
        return notificationRecordRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在: " + notificationId));
    }
}
