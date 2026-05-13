package io.github.matrixlane.metadataregistry.web.listener;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.MappingScheme;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.event.MappingChangedEvent;
import io.github.matrixlane.metadataregistry.web.service.MappingChangeNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link MappingChangedEventListener}.
 *
 * <p>Verifies that mapping change events are correctly forwarded
 * to the {@link MappingChangeNotificationService}.
 */
@ExtendWith(MockitoExtension.class)
class MappingChangedEventListenerTest {

    @Mock
    private MappingChangeNotificationService notificationService;

    private MappingChangedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new MappingChangedEventListener(notificationService);
    }

    @Test
    void onMappingCreated_shouldNotifySubscribers() {
        UUID mappingId = UUID.randomUUID();
        ItemMapping mapping = createMapping(mappingId);

        MappingChangedEvent event = new MappingChangedEvent(
                this, mappingId, "CREATED", mapping);

        listener.onMappingChanged(event);

        verify(notificationService).notifyMappingChanged(mapping, "CREATED");
    }

    @Test
    void onMappingDeleted_shouldNotifySubscribers() {
        UUID mappingId = UUID.randomUUID();
        ItemMapping mapping = createMapping(mappingId);

        MappingChangedEvent event = new MappingChangedEvent(
                this, mappingId, "DELETED", mapping);

        listener.onMappingChanged(event);

        verify(notificationService).notifyMappingChanged(mapping, "DELETED");
    }

    @Test
    void onMappingChanged_withNullMapping_shouldSkipNotification() {
        UUID mappingId = UUID.randomUUID();

        MappingChangedEvent event = new MappingChangedEvent(
                this, mappingId, "DELETED", null);

        listener.onMappingChanged(event);

        verify(notificationService, never()).notifyMappingChanged(any(), any());
    }

    @Test
    void onMappingChanged_withNullScheme_shouldSkipNotification() {
        UUID mappingId = UUID.randomUUID();
        ItemMapping mapping = new ItemMapping(
                ItemMappingSubject.forItem(UUID.randomUUID()),
                ItemMappingObject.forItem(UUID.randomUUID()),
                MappingType.EQUIVALENT
        );
        // mapping.getMappingScheme() returns null by default

        MappingChangedEvent event = new MappingChangedEvent(
                this, mappingId, "CREATED", mapping);

        listener.onMappingChanged(event);

        verify(notificationService, never()).notifyMappingChanged(any(), any());
    }

    @Test
    void onMappingChanged_notificationFailure_shouldNotPropagate() {
        UUID mappingId = UUID.randomUUID();
        ItemMapping mapping = createMapping(mappingId);

        MappingChangedEvent event = new MappingChangedEvent(
                this, mappingId, "CREATED", mapping);

        // Use lenient stubbing because the exception is the test assertion,
        // not a verification target
        org.mockito.Mockito.lenient().doThrow(new RuntimeException("Notification failed"))
                .when(notificationService).notifyMappingChanged(any(), any());

        // Should not throw
        listener.onMappingChanged(event);
    }

    private ItemMapping createMapping(UUID mappingId) {
        ItemMappingSubject subject = ItemMappingSubject.forItem(UUID.randomUUID());
        ItemMappingObject object = ItemMappingObject.forItem(UUID.randomUUID());
        ItemMapping mapping = new ItemMapping(subject, object, MappingType.EQUIVALENT);
        
        // Set a mapping scheme so the listener forwards the event
        MappingScheme scheme = new MappingScheme();
        try {
            java.lang.reflect.Field schemeIdField = MappingScheme.class.getDeclaredField("id");
            schemeIdField.setAccessible(true);
            schemeIdField.set(scheme, UUID.randomUUID());
            
            java.lang.reflect.Field schemeNameField = MappingScheme.class.getDeclaredField("name");
            schemeNameField.setAccessible(true);
            schemeNameField.set(scheme, "Test Scheme");
            
            java.lang.reflect.Field mappingSchemeField = ItemMapping.class.getDeclaredField("mappingScheme");
            mappingSchemeField.setAccessible(true);
            mappingSchemeField.set(mapping, scheme);
            
            java.lang.reflect.Field idField = ItemMapping.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(mapping, mappingId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return mapping;
    }
}
