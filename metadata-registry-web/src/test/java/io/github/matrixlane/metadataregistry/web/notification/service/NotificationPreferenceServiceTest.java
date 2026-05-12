package io.github.matrixlane.metadataregistry.web.notification.service;

import io.github.matrixlane.metadataregistry.web.notification.dto.NotificationPreferenceRequest;
import io.github.matrixlane.metadataregistry.web.notification.dto.NotificationPreferenceResponse;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationPreference;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import io.github.matrixlane.metadataregistry.web.notification.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    private NotificationPreferenceService preferenceService;

    @Test
    void shouldReturnDefaultsWhenNoPreferencesExist() {
        when(preferenceRepository.findByUserId("user1")).thenReturn(Collections.emptyList());

        List<NotificationPreferenceResponse> result = preferenceService.getPreferences("user1");

        assertThat(result).hasSize(NotificationType.values().length * NotificationChannel.values().length);
        result.stream()
                .filter(r -> r.getChannel() == NotificationChannel.IN_APP)
                .forEach(r -> assertThat(r.isEnabled()).isTrue());
        result.stream()
                .filter(r -> r.getChannel() == NotificationChannel.EMAIL)
                .forEach(r -> assertThat(r.isEnabled()).isFalse());
    }

    @Test
    void shouldReturnExistingPreferences() {
        NotificationPreference pref = NotificationPreference.of(
                "user1", NotificationType.APPROVAL_SUBMITTED, NotificationChannel.EMAIL, true);

        when(preferenceRepository.findByUserId("user1")).thenReturn(List.of(pref));

        List<NotificationPreferenceResponse> result = preferenceService.getPreferences("user1");

        Optional<NotificationPreferenceResponse> found = result.stream()
                .filter(r -> r.getNotificationType() == NotificationType.APPROVAL_SUBMITTED
                        && r.getChannel() == NotificationChannel.EMAIL)
                .findFirst();

        assertThat(found).isPresent();
        assertThat(found.get().isEnabled()).isTrue();
    }

    @Test
    void shouldUpdateExistingPreference() {
        NotificationPreference existing = NotificationPreference.of(
                "user1", NotificationType.APPROVAL_SUBMITTED, NotificationChannel.EMAIL, false);

        when(preferenceRepository.findByUserIdAndNotificationTypeAndChannel(
                "user1", NotificationType.APPROVAL_SUBMITTED, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(existing));
        when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(i -> i.getArgument(0));

        NotificationPreferenceRequest request = new NotificationPreferenceRequest(
                NotificationType.APPROVAL_SUBMITTED, NotificationChannel.EMAIL, true);

        NotificationPreferenceResponse result = preferenceService.updatePreference("user1", request);

        assertThat(result.isEnabled()).isTrue();
        assertThat(existing.isEnabled()).isTrue();
    }

    @Test
    void shouldCreateNewPreferenceWhenNotExists() {
        when(preferenceRepository.findByUserIdAndNotificationTypeAndChannel(
                "user1", NotificationType.APPROVAL_SUBMITTED, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(i -> i.getArgument(0));

        NotificationPreferenceRequest request = new NotificationPreferenceRequest(
                NotificationType.APPROVAL_SUBMITTED, NotificationChannel.EMAIL, true);

        NotificationPreferenceResponse result = preferenceService.updatePreference("user1", request);

        assertThat(result.isEnabled()).isTrue();
        verify(preferenceRepository).save(any(NotificationPreference.class));
    }

    @Test
    void shouldInitializeDefaults() {
        when(preferenceRepository.findByUserIdAndNotificationTypeAndChannel(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(i -> i.getArgument(0));

        preferenceService.initializeDefaults("user1");

        int expectedCount = NotificationType.values().length * NotificationChannel.values().length;
        verify(preferenceRepository, org.mockito.Mockito.times(expectedCount)).save(any(NotificationPreference.class));
    }

    @Test
    void shouldNotDuplicateOnInitialize() {
        NotificationPreference existing = NotificationPreference.of(
                "user1", NotificationType.APPROVAL_SUBMITTED, NotificationChannel.IN_APP, true);

        when(preferenceRepository.findByUserIdAndNotificationTypeAndChannel(
                "user1", NotificationType.APPROVAL_SUBMITTED, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(existing));
        when(preferenceRepository.findByUserIdAndNotificationTypeAndChannel(
                "user1", NotificationType.APPROVAL_SUBMITTED, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(i -> i.getArgument(0));

        preferenceService.initializeDefaults("user1");

        int expectedSaves = NotificationType.values().length // EMAIL for each type
                + (NotificationType.values().length - 1); // IN_APP for other types except APPROVAL_SUBMITTED
        verify(preferenceRepository, org.mockito.Mockito.times(expectedSaves)).save(any(NotificationPreference.class));
    }
}
