package io.github.matrixlane.metadataregistry.web.notification.event;

import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import io.github.matrixlane.metadataregistry.web.notification.service.EmailNotificationService;
import io.github.matrixlane.metadataregistry.web.notification.service.InAppNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private InAppNotificationService inAppService;

    @Mock
    private EmailNotificationService emailService;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    void shouldSendBothChannelsWhenEnabled() {
        when(inAppService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).thenReturn(true);
        when(emailService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).thenReturn(true);

        NotificationEvent event = NotificationEvent.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                Map.of("itemName", "测试项"), "biz-123");

        listener.handleNotificationEvent(event);

        verify(inAppService).sendWithTemplate(eq("user1"), eq(NotificationType.APPROVAL_SUBMITTED),
                any(Map.class), eq("biz-123"));
        verify(emailService).sendWithTemplate(eq("user1"), eq(NotificationType.APPROVAL_SUBMITTED),
                any(Map.class), eq("biz-123"));
    }

    @Test
    void shouldSkipInAppWhenDisabled() {
        when(inAppService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).thenReturn(false);
        when(emailService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).thenReturn(true);

        NotificationEvent event = NotificationEvent.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                Map.of("itemName", "测试项"), "biz-123");

        listener.handleNotificationEvent(event);

        verify(inAppService, never()).sendWithTemplate(any(), any(), any(), any());
        verify(emailService).sendWithTemplate(eq("user1"), eq(NotificationType.APPROVAL_SUBMITTED),
                any(Map.class), eq("biz-123"));
    }

    @Test
    void shouldSkipEmailWhenDisabled() {
        when(inAppService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).thenReturn(true);
        when(emailService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).thenReturn(false);

        NotificationEvent event = NotificationEvent.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                Map.of("itemName", "测试项"), "biz-123");

        listener.handleNotificationEvent(event);

        verify(inAppService).sendWithTemplate(eq("user1"), eq(NotificationType.APPROVAL_SUBMITTED),
                any(Map.class), eq("biz-123"));
        verify(emailService, never()).sendWithTemplate(any(), any(), any(), any());
    }

    @Test
    void shouldNotCrashWhenInAppThrows() {
        when(inAppService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).thenReturn(true);
        when(emailService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).thenReturn(true);
        doThrow(new RuntimeException("in-app error")).when(inAppService).sendWithTemplate(
                any(), any(), any(), any());

        NotificationEvent event = NotificationEvent.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                Map.of("itemName", "测试项"), "biz-123");

        listener.handleNotificationEvent(event);

        verify(emailService).sendWithTemplate(eq("user1"), eq(NotificationType.APPROVAL_SUBMITTED),
                any(Map.class), eq("biz-123"));
    }

    @Test
    void shouldNotCrashWhenEmailThrows() {
        when(inAppService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).thenReturn(true);
        when(emailService.isEnabled("user1", NotificationType.APPROVAL_SUBMITTED)).thenReturn(true);
        doThrow(new RuntimeException("email error")).when(emailService).sendWithTemplate(
                any(), any(), any(), any());

        NotificationEvent event = NotificationEvent.of(
                "user1", NotificationType.APPROVAL_SUBMITTED,
                Map.of("itemName", "测试项"), "biz-123");

        listener.handleNotificationEvent(event);

        verify(inAppService).sendWithTemplate(eq("user1"), eq(NotificationType.APPROVAL_SUBMITTED),
                any(Map.class), eq("biz-123"));
    }
}
