package io.github.matrixlane.metadataregistry.web.notification.service;

import io.github.matrixlane.metadataregistry.web.auth.AppUser;
import io.github.matrixlane.metadataregistry.web.auth.AppUserRepository;
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
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRetryServiceTest {

    @Mock
    private NotificationRecordRepository recordRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private NotificationRetryService retryService;

    @Test
    void shouldDoNothingWhenNoPendingRetries() {
        when(recordRepository.findPendingRetries(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        retryService.retryPendingNotifications();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldRetryEmailSuccessfully() {
        NotificationRecord record = createEmailRecord(1);
        record.setNextRetryAt(LocalDateTime.now().minusMinutes(1));

        AppUser user = new AppUser();
        user.setUsername("user1");
        user.setEmail("user1@example.com");

        when(recordRepository.findPendingRetries(any(LocalDateTime.class)))
                .thenReturn(List.of(record));
        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(recordRepository.save(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));

        retryService.retryPendingNotifications();

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(record.getRetryCount()).isEqualTo(1);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldFailRetryWhenNoEmail() {
        NotificationRecord record = createEmailRecord(2);
        record.setNextRetryAt(LocalDateTime.now().minusMinutes(1));

        AppUser user = new AppUser();
        user.setUsername("user1");
        user.setEmail(null);

        when(recordRepository.findPendingRetries(any(LocalDateTime.class)))
                .thenReturn(List.of(record));
        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(recordRepository.save(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));

        retryService.retryPendingNotifications();

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.FAILED);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldFailPermanentlyAfterMaxRetries() {
        NotificationRecord record = createEmailRecord(3);
        record.setNextRetryAt(LocalDateTime.now().minusMinutes(1));

        when(recordRepository.findPendingRetries(any(LocalDateTime.class)))
                .thenReturn(List.of(record));
        when(recordRepository.save(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));

        retryService.retryPendingNotifications();

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.FAILED);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldIncrementRetryCountOnFailure() {
        NotificationRecord record = createEmailRecord(1);
        record.setNextRetryAt(LocalDateTime.now().minusMinutes(1));

        AppUser user = new AppUser();
        user.setUsername("user1");
        user.setEmail("user1@example.com");

        when(recordRepository.findPendingRetries(any(LocalDateTime.class)))
                .thenReturn(List.of(record));
        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        when(recordRepository.save(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));

        retryService.retryPendingNotifications();

        assertThat(record.getRetryCount()).isEqualTo(2);
        assertThat(record.getErrorMessage()).contains("SMTP down");
    }

    @Test
    void shouldMarkInAppAsSent() {
        NotificationRecord record = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.IN_APP, "标题", "内容", "biz-123");
        record.setRetryCount(1);
        record.setNextRetryAt(LocalDateTime.now().minusMinutes(1));

        when(recordRepository.findPendingRetries(any(LocalDateTime.class)))
                .thenReturn(List.of(record));
        when(recordRepository.save(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));

        retryService.retryPendingNotifications();

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    private NotificationRecord createEmailRecord(int retryCount) {
        NotificationRecord record = NotificationRecord.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                NotificationChannel.EMAIL, "标题", "内容", "biz-123");
        record.setRetryCount(retryCount);
        record.setStatus(NotificationStatus.PENDING);
        return record;
    }
}
