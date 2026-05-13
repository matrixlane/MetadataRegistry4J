package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.MappingScheme;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.notification.MappingSubscription;
import io.github.matrixlane.metadataregistry.itemmapping.notification.MappingSubscriptionService;
import io.github.matrixlane.metadataregistry.itemmapping.notification.SubscriptionType;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationRecord;
import io.github.matrixlane.metadataregistry.web.notification.service.InAppNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MappingChangeNotificationServiceTest {

    @Mock
    private MappingSubscriptionService subscriptionService;

    @Mock
    private InAppNotificationService inAppNotificationService;

    private MappingChangeNotificationService service;

    @BeforeEach
    void setUp() {
        service = new MappingChangeNotificationService(subscriptionService, inAppNotificationService);
    }

    private ItemMapping createMapping(UUID schemeId, UUID subjectId, UUID objectId) {
        MappingScheme scheme = new MappingScheme("Test", "src", "tgt", "desc");
        scheme.setId(schemeId);
        ItemMappingSubject sub = ItemMappingSubject.forItem(subjectId);
        sub.setId(UUID.randomUUID());
        ItemMappingObject obj = ItemMappingObject.forItem(objectId);
        obj.setId(UUID.randomUUID());
        ItemMapping mapping = new ItemMapping(sub, obj, MappingType.EQUIVALENT, "desc", null, scheme);
        mapping.setId(UUID.randomUUID());
        return mapping;
    }

    @Test
    void notifyMappingChanged_shouldNotifySchemeSubscribers() {
        UUID schemeId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        ItemMapping mapping = createMapping(schemeId, subjectId, objectId);

        MappingSubscription sub = new MappingSubscription("user-1", SubscriptionType.SCHEME, schemeId);

        when(subscriptionService.findSchemeSubscribers(List.of(schemeId)))
                .thenReturn(List.of(sub));
        when(subscriptionService.findItemSubscribers(List.of(subjectId, objectId)))
                .thenReturn(List.of());
        when(inAppNotificationService.isEnabled(eq("user-1"), any()))
                .thenReturn(true);
        when(inAppNotificationService.send(any(), any(), any(), any(), any()))
                .thenReturn(new NotificationRecord());

        service.notifyMappingChanged(mapping, "MODIFIED");

        verify(inAppNotificationService).send(eq("user-1"), any(), any(), any(), any());
    }

    @Test
    void notifyMappingChanged_shouldNotifyItemSubscribers() {
        UUID schemeId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        ItemMapping mapping = createMapping(schemeId, subjectId, objectId);

        MappingSubscription sub = new MappingSubscription("user-2", SubscriptionType.ITEM, subjectId);

        when(subscriptionService.findSchemeSubscribers(List.of(schemeId)))
                .thenReturn(List.of());
        when(subscriptionService.findItemSubscribers(List.of(subjectId, objectId)))
                .thenReturn(List.of(sub));
        when(inAppNotificationService.isEnabled(eq("user-2"), any()))
                .thenReturn(true);
        when(inAppNotificationService.send(any(), any(), any(), any(), any()))
                .thenReturn(new NotificationRecord());

        service.notifyMappingChanged(mapping, "CREATED");

        verify(inAppNotificationService).send(eq("user-2"), any(), any(), any(), any());
    }

    @Test
    void notifyMappingChanged_shouldDeduplicateUsers() {
        UUID schemeId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        ItemMapping mapping = createMapping(schemeId, subjectId, objectId);

        // Same user subscribes to both scheme and item
        MappingSubscription schemeSub = new MappingSubscription("user-1", SubscriptionType.SCHEME, schemeId);
        MappingSubscription itemSub = new MappingSubscription("user-1", SubscriptionType.ITEM, subjectId);

        when(subscriptionService.findSchemeSubscribers(List.of(schemeId)))
                .thenReturn(List.of(schemeSub));
        when(subscriptionService.findItemSubscribers(List.of(subjectId, objectId)))
                .thenReturn(List.of(itemSub));
        when(inAppNotificationService.isEnabled(eq("user-1"), any()))
                .thenReturn(true);
        when(inAppNotificationService.send(any(), any(), any(), any(), any()))
                .thenReturn(new NotificationRecord());

        service.notifyMappingChanged(mapping, "MODIFIED");

        // Should only send once despite two subscriptions
        verify(inAppNotificationService).send(eq("user-1"), any(), any(), any(), any());
    }

    @Test
    void notifyMappingChanged_disabledPreference_shouldNotSend() {
        UUID schemeId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();
        ItemMapping mapping = createMapping(schemeId, subjectId, objectId);

        MappingSubscription sub = new MappingSubscription("user-1", SubscriptionType.SCHEME, schemeId);

        when(subscriptionService.findSchemeSubscribers(List.of(schemeId)))
                .thenReturn(List.of(sub));
        when(subscriptionService.findItemSubscribers(List.of(subjectId, objectId)))
                .thenReturn(List.of());
        when(inAppNotificationService.isEnabled(eq("user-1"), any()))
                .thenReturn(false);

        service.notifyMappingChanged(mapping, "DELETED");

        verify(inAppNotificationService, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    void notifyMappingChanged_noScheme_shouldDoNothing() {
        ItemMappingSubject sub = ItemMappingSubject.forItem(UUID.randomUUID());
        ItemMappingObject obj = ItemMappingObject.forItem(UUID.randomUUID());
        ItemMapping mapping = new ItemMapping(sub, obj, MappingType.EQUIVALENT);
        mapping.setId(UUID.randomUUID());
        // No mapping scheme set

        service.notifyMappingChanged(mapping, "MODIFIED");

        verify(subscriptionService, never()).findSchemeSubscribers(any());
        verify(inAppNotificationService, never()).send(any(), any(), any(), any(), any());
    }
}
