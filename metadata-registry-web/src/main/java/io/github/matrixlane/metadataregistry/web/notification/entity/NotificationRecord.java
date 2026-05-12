package io.github.matrixlane.metadataregistry.web.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_record")
@Getter
@Setter
@NoArgsConstructor
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_id", nullable = false, length = 64)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 32)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannel channel;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "content", nullable = false, length = 4000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "related_business_id", length = 64)
    private String relatedBusinessId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public static NotificationRecord of(String recipientId, NotificationType notificationType,
                                        NotificationChannel channel, String title, String content,
                                        String relatedBusinessId) {
        NotificationRecord record = new NotificationRecord();
        record.setRecipientId(recipientId);
        record.setNotificationType(notificationType);
        record.setChannel(channel);
        record.setTitle(title);
        record.setContent(content);
        record.setRelatedBusinessId(relatedBusinessId);
        record.setStatus(NotificationStatus.PENDING);
        record.setRetryCount(0);
        record.setMaxRetries(3);
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.retryCount++;
        if (this.retryCount < this.maxRetries) {
            this.nextRetryAt = LocalDateTime.now().plusMinutes(5);
            this.status = NotificationStatus.PENDING;
        } else {
            this.status = NotificationStatus.FAILED;
        }
    }

    public boolean canRetry() {
        return this.retryCount < this.maxRetries;
    }
}
