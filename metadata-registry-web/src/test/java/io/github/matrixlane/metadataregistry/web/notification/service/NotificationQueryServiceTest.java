package io.github.matrixlane.metadataregistry.web.notification.service;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationRecord;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationStatus;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import io.github.matrixlane.metadataregistry.web.notification.repository.NotificationRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationRecordRepository recordRepository;

    @InjectMocks
    private NotificationQueryService queryService;

    @Test
    void shouldGetInbox() {
        Pageable pageable = PageRequest.of(0, 10);
        NotificationRecord record = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.IN_APP, "标题", "内容", "biz-123");
        record.markSent();

        when(recordRepository.findByRecipientIdOrderByCreatedAtDesc("user1", pageable))
                .thenReturn(new PageImpl<>(List.of(record)));

        Page<NotificationRecord> result = queryService.getInbox("user1", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRecipientId()).isEqualTo("user1");
    }

    @Test
    void shouldGetUnreadNotifications() {
        NotificationRecord record = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.IN_APP, "标题", "内容", "biz-123");
        record.markSent();

        when(recordRepository.findUnreadByRecipient(
                eq("user1"), eq(NotificationChannel.IN_APP), any(List.class)))
                .thenReturn(List.of(record));

        List<NotificationRecord> result = queryService.getUnreadNotifications("user1");

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldCountUnread() {
        when(recordRepository.countByRecipientIdAndChannelAndStatusIn(
                eq("user1"), eq(NotificationChannel.IN_APP), any(List.class)))
                .thenReturn(5L);

        long count = queryService.countUnread("user1");

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void shouldMarkAsRead() {
        UUID id = UUID.randomUUID();
        NotificationRecord record = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.IN_APP, "标题", "内容", "biz-123");
        record.setId(id);
        record.setStatus(NotificationStatus.PENDING);

        when(recordRepository.findById(id)).thenReturn(Optional.of(record));
        when(recordRepository.save(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));

        NotificationRecord result = queryService.markAsRead(id);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void shouldNotChangeAlreadySentStatus() {
        UUID id = UUID.randomUUID();
        NotificationRecord record = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.IN_APP, "标题", "内容", "biz-123");
        record.setId(id);
        record.markSent();

        when(recordRepository.findById(id)).thenReturn(Optional.of(record));

        NotificationRecord result = queryService.markAsRead(id);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void shouldThrowWhenNotificationNotFound() {
        UUID id = UUID.randomUUID();
        when(recordRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getNotification(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("通知不存在");
    }

    @Test
    void shouldGetNotificationDetail() {
        UUID id = UUID.randomUUID();
        NotificationRecord record = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.IN_APP, "标题", "内容", "biz-123");
        record.setId(id);

        when(recordRepository.findById(id)).thenReturn(Optional.of(record));

        NotificationRecord result = queryService.getNotification(id);

        assertThat(result.getId()).isEqualTo(id);
    }
}
