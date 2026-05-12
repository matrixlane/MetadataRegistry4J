package io.github.matrixlane.metadataregistry.web.notification.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRecordTest {

    @Test
    void shouldCreatePendingRecord() {
        NotificationRecord record = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.IN_APP, "标题", "内容", "biz-123");

        assertThat(record.getRecipientId()).isEqualTo("user1");
        assertThat(record.getNotificationType()).isEqualTo(NotificationType.APPROVAL_SUBMITTED);
        assertThat(record.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(record.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(record.getRetryCount()).isZero();
        assertThat(record.getMaxRetries()).isEqualTo(3);
        assertThat(record.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldMarkSent() {
        NotificationRecord record = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.IN_APP, "标题", "内容", "biz-123");

        record.markSent();

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(record.getSentAt()).isNotNull();
        assertThat(record.getErrorMessage()).isNull();
    }

    @Test
    void shouldMarkFailedAndScheduleRetry() {
        NotificationRecord record = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.EMAIL, "标题", "内容", "biz-123");

        record.markFailed("SMTP timeout");

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(record.getRetryCount()).isEqualTo(1);
        assertThat(record.getErrorMessage()).isEqualTo("SMTP timeout");
        assertThat(record.getNextRetryAt()).isNotNull();
    }

    @Test
    void shouldMarkFailedAfterMaxRetries() {
        NotificationRecord record = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.EMAIL, "标题", "内容", "biz-123");
        record.setRetryCount(2);

        record.markFailed("SMTP timeout");

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(record.getRetryCount()).isEqualTo(3);
        assertThat(record.canRetry()).isFalse();
    }

    @Test
    void shouldAllowRetryWhenBelowMax() {
        NotificationRecord record = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.EMAIL, "标题", "内容", "biz-123");
        record.setRetryCount(1);

        assertThat(record.canRetry()).isTrue();
    }

    @Test
    void shouldNotAllowRetryAtMax() {
        NotificationRecord record = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.EMAIL, "标题", "内容", "biz-123");
        record.setRetryCount(3);

        assertThat(record.canRetry()).isFalse();
    }
}
