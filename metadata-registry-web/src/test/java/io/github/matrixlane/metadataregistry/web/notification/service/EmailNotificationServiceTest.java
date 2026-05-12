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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private NotificationRecordRepository recordRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    private EmailNotificationService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@example.com");
    }

    @Test
    void shouldReturnEmailChannel() {
        assertThat(emailService.getChannel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    void shouldSendEmailWhenUserHasEmail() {
        AppUser user = new AppUser();
        user.setUsername("user1");
        user.setEmail("user1@example.com");

        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(recordRepository.save(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));

        NotificationRecord result = emailService.send(
                "user1", NotificationType.APPROVAL_SUBMITTED, "标题", "内容", "biz-123");

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldSkipWhenUserHasNoEmail() {
        AppUser user = new AppUser();
        user.setUsername("user1");
        user.setEmail(null);

        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(recordRepository.save(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));

        NotificationRecord result = emailService.send(
                "user1", NotificationType.APPROVAL_SUBMITTED, "标题", "内容", "biz-123");

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldSkipWhenUserNotFound() {
        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.empty());
        when(recordRepository.save(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));

        NotificationRecord result = emailService.send(
                "user1", NotificationType.APPROVAL_SUBMITTED, "标题", "内容", "biz-123");

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldMarkFailedOnMailException() {
        AppUser user = new AppUser();
        user.setUsername("user1");
        user.setEmail("user1@example.com");

        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));
        when(recordRepository.save(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));

        NotificationRecord result = emailService.send(
                "user1", NotificationType.APPROVAL_SUBMITTED, "标题", "内容", "biz-123");

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getErrorMessage()).contains("SMTP error");
    }

    @Test
    void shouldSendWithTemplate() {
        AppUser user = new AppUser();
        user.setUsername("user1");
        user.setEmail("user1@example.com");

        when(appUserRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(recordRepository.save(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> vars = Map.of(
                "itemName", "测试项",
                "approverName", "admin",
                "submitterName", "user1",
                "opinion", ""
        );

        NotificationRecord result = emailService.sendWithTemplate(
                "user1", NotificationType.APPROVAL_SUBMITTED, vars, "biz-123");

        assertThat(result).isNotNull();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldBeDisabledByDefault() {
        when(preferenceRepository.findByUserIdAndNotificationTypeAndChannel(
                "user1", NotificationType.APPROVAL_SUBMITTED, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());

        assertThat(emailService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).isFalse();
    }

    @Test
    void shouldRespectEnabledPreference() {
        NotificationPreference pref = NotificationPreference.of(
                "user1", NotificationType.APPROVAL_SUBMITTED, NotificationChannel.EMAIL, true);

        when(preferenceRepository.findByUserIdAndNotificationTypeAndChannel(
                "user1", NotificationType.APPROVAL_SUBMITTED, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(pref));

        assertThat(emailService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).isTrue();
    }
}
