package io.github.matrixlane.metadataregistry.web.notification.service;

import io.github.matrixlane.metadataregistry.web.notification.dto.NotificationPreferenceRequest;
import io.github.matrixlane.metadataregistry.web.notification.dto.NotificationPreferenceResponse;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationPreference;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import io.github.matrixlane.metadataregistry.web.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getPreferences(String userId) {
        List<NotificationPreference> existing = preferenceRepository.findByUserId(userId);

        return Arrays.stream(NotificationType.values())
                .flatMap(type -> Arrays.stream(NotificationChannel.values())
                        .map(channel -> findOrDefault(existing, userId, type, channel)))
                .collect(Collectors.toList());
    }

    public NotificationPreferenceResponse updatePreference(String userId,
                                                           NotificationPreferenceRequest request) {
        Optional<NotificationPreference> existing = preferenceRepository
                .findByUserIdAndNotificationTypeAndChannel(userId, request.getNotificationType(),
                        request.getChannel());

        NotificationPreference preference;
        if (existing.isPresent()) {
            preference = existing.get();
            preference.toggle(request.isEnabled());
        } else {
            preference = NotificationPreference.of(userId, request.getNotificationType(),
                    request.getChannel(), request.isEnabled());
        }
        preferenceRepository.save(preference);
        return toResponse(preference);
    }

    public void initializeDefaults(String userId) {
        for (NotificationType type : NotificationType.values()) {
            ensurePreference(userId, type, NotificationChannel.IN_APP, true);
            ensurePreference(userId, type, NotificationChannel.EMAIL, false);
        }
    }

    private void ensurePreference(String userId, NotificationType type,
                                  NotificationChannel channel, boolean enabled) {
        if (preferenceRepository.findByUserIdAndNotificationTypeAndChannel(userId, type, channel).isEmpty()) {
            preferenceRepository.save(NotificationPreference.of(userId, type, channel, enabled));
        }
    }

    private NotificationPreferenceResponse findOrDefault(List<NotificationPreference> existing,
                                                          String userId, NotificationType type,
                                                          NotificationChannel channel) {
        return existing.stream()
                .filter(p -> p.getNotificationType() == type && p.getChannel() == channel)
                .findFirst()
                .map(this::toResponse)
                .orElseGet(() -> new NotificationPreferenceResponse(
                        null, userId, type, channel,
                        channel == NotificationChannel.IN_APP
                ));
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                preference.getId(),
                preference.getUserId(),
                preference.getNotificationType(),
                preference.getChannel(),
                preference.isEnabled()
        );
    }
}
