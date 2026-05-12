package io.github.matrixlane.metadataregistry.web.notification.service;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationPreference;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationRecord;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationStatus;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import io.github.matrixlane.metadataregistry.web.notification.repository.NotificationPreferenceRepository;
import io.github.matrixlane.metadataregistry.web.notification.repository.NotificationRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceTest {

    @Mock
    private NotificationRecordRepository recordRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    private InAppNotificationService inAppService;

    @Test
    void shouldReturnInAppChannel() {
        assertThat(inAppService.getChannel()).isEqualTo(NotificationChannel.IN_APP);
    }

    @Test
    void shouldSendNotification() {
        NotificationRecord saved = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.IN_APP, "标题", "内容", "biz-123");
        saved.markSent();

        when(recordRepository.save(any(NotificationRecord.class))).thenReturn(saved);

        NotificationRecord result = inAppService.send(
                "user1", NotificationType.APPROVAL_SUBMITTED, "标题", "内容", "biz-123");

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        verify(recordRepository).save(any(NotificationRecord.class));
    }

    @Test
    void shouldSendWithTemplate() {
        NotificationRecord saved = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.IN_APP, "标题", "内容", "biz-123");
        saved.markSent();

        when(recordRepository.save(any(NotificationRecord.class))).thenReturn(saved);

        Map<String, Object> vars = Map.of(
                "itemName", "测试项",
                "approverName", "admin",
                "submitterName", "user1",
                "opinion", ""
        );

        NotificationRecord result = inAppService.sendWithTemplate(
                "user1", NotificationType.APPROVAL_SUBMITTED, vars, "biz-123");

        assertThat(result).isNotNull();
        verify(recordRepository).save(any(NotificationRecord.class));
    }

    @Test
    void shouldBeEnabledByDefault() {
        when(preferenceRepository.findByUserIdAndNotificationTypeAndChannel(
                "user1", NotificationType.APPROVAL_SUBMITTED, NotificationChannel.IN_APP))
                .thenReturn(Optional.empty());

        assertThat(inAppService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).isTrue();
    }

    @Test
    void shouldRespectDisabledPreference() {
        NotificationPreference pref = NotificationPreference.of(
                "user1", NotificationType.APPROVAL_SUBMITTED, NotificationChannel.IN_APP, false);

        when(preferenceRepository.findByUserIdAndNotificationTypeAndChannel(
                "user1", NotificationType.APPROVAL_SUBMITTED, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(pref));

        assertThat(inAppService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).isFalse();
    }
}
