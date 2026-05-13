package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.notification.MappingSubscription;
import io.github.matrixlane.metadataregistry.itemmapping.notification.MappingSubscriptionService;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationChannel;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import io.github.matrixlane.metadataregistry.web.notification.service.InAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for notifying subscribers about mapping changes.
 *
 * <p>V1: In-app notifications only. Integrates with the existing
 * {@link InAppNotificationService} to deliver notifications.
 *
 * <p>Future versions may add email and webhook channels.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MappingChangeNotificationService {

    private final MappingSubscriptionService subscriptionService;
    private final InAppNotificationService inAppNotificationService;

    /**
     * Notifies subscribers when a mapping has changed.
     *
     * @param mapping  the mapping that changed
     * @param changeType description of the change (e.g. "CREATED", "MODIFIED", "DELETED")
     */
    @Transactional
    public void notifyMappingChanged(ItemMapping mapping, String changeType) {
        if (mapping.getMappingScheme() == null) {
            return;
        }

        UUID schemeId = mapping.getMappingScheme().getId();
        UUID subjectId = mapping.getSubject().getItemId();
        UUID objectId = mapping.getObject().getItemId();

        // Collect target IDs for subscription lookup
        List<UUID> schemeIds = List.of(schemeId);
        List<UUID> itemIds = new java.util.ArrayList<>();
        if (subjectId != null) itemIds.add(subjectId);
        if (objectId != null) itemIds.add(objectId);

        // Find subscribers
        List<MappingSubscription> schemeSubscribers = subscriptionService.findSchemeSubscribers(schemeIds);
        List<MappingSubscription> itemSubscribers = itemIds.isEmpty()
                ? List.of()
                : subscriptionService.findItemSubscribers(itemIds);

        // Deduplicate by userId
        Set<String> notifiedUsers = new java.util.HashSet<>();

        String title = buildTitle(mapping, changeType);
        String content = buildContent(mapping, changeType);
        String businessId = mapping.getId().toString();

        for (MappingSubscription sub : schemeSubscribers) {
            if (notifiedUsers.add(sub.getUserId())) {
                sendIfEnabled(sub.getUserId(), title, content, businessId);
            }
        }

        for (MappingSubscription sub : itemSubscribers) {
            if (notifiedUsers.add(sub.getUserId())) {
                sendIfEnabled(sub.getUserId(), title, content, businessId);
            }
        }

        log.debug("Notified {} users about mapping change {}", notifiedUsers.size(), mapping.getId());
    }

    private void sendIfEnabled(String userId, String title, String content, String businessId) {
        try {
            if (inAppNotificationService.isEnabled(userId, NotificationType.MAPPING_CHANGED)) {
                inAppNotificationService.send(userId, NotificationType.MAPPING_CHANGED,
                        title, content, businessId);
            }
        } catch (Exception e) {
            log.warn("Failed to send mapping change notification to user {}: {}", userId, e.getMessage());
        }
    }

    private String buildTitle(ItemMapping mapping, String changeType) {
        return String.format("映射%s: %s → %s",
                changeType,
                mapping.getSubject().getItemId() != null
                        ? mapping.getSubject().getItemId().toString().substring(0, 8)
                        : "External",
                mapping.getObject().getItemId() != null
                        ? mapping.getObject().getItemId().toString().substring(0, 8)
                        : "External");
    }

    private String buildContent(ItemMapping mapping, String changeType) {
        return String.format("您订阅的映射关系已被%s。映射类型: %s，方案: %s",
                changeType,
                mapping.getMappingType(),
                mapping.getMappingScheme() != null ? mapping.getMappingScheme().getName() : "N/A");
    }
}
