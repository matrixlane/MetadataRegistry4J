package io.github.matrixlane.metadataregistry.web.listener;

import io.github.matrixlane.metadataregistry.itemmapping.event.MappingChangedEvent;
import io.github.matrixlane.metadataregistry.web.service.MappingChangeNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link MappingChangedEvent} and triggers subscriber notifications.
 *
 * <p>Uses {@link TransactionalEventListener} to ensure notifications are only
 * sent after the transaction that changed the mapping has committed successfully.
 * This avoids notifying subscribers about changes that may later be rolled back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MappingChangedEventListener {

    private final MappingChangeNotificationService notificationService;

    @TransactionalEventListener(fallbackExecution = true)
    public void onMappingChanged(MappingChangedEvent event) {
        if (event.getMapping() == null || event.getMapping().getMappingScheme() == null) {
            log.debug("Skipping notification for mapping {}: no scheme associated", event.getMappingId());
            return;
        }

        try {
            notificationService.notifyMappingChanged(event.getMapping(), event.getChangeType());
            log.debug("Processed mapping changed event for mapping {}", event.getMappingId());
        } catch (Exception e) {
            log.error("Failed to process mapping changed event for mapping {}: {}",
                    event.getMappingId(), e.getMessage(), e);
        }
    }
}
