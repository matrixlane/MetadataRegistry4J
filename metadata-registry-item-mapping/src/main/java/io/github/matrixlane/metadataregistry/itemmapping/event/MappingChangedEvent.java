package io.github.matrixlane.metadataregistry.itemmapping.event;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Event published when a mapping relationship is created, modified, or deleted.
 *
 * <p>Subscribers in other modules (e.g. web notification layer) can listen
 * to this event to trigger notifications, impact analysis, audit logging, etc.
 *
 * <p>V1: Published for CREATED and DELETED operations.
 * Future versions may add MODIFIED events when mapping updates are supported.
 */
public class MappingChangedEvent extends ApplicationEvent {

    private final UUID mappingId;
    private final String changeType;
    private final ItemMapping mapping;

    /**
     * Creates a new mapping changed event.
     *
     * @param source     the component that published the event (typically the service)
     * @param mappingId  the UUID of the affected mapping
     * @param changeType the type of change: "CREATED", "MODIFIED", or "DELETED"
     * @param mapping    the mapping entity (may be null for DELETE events)
     */
    public MappingChangedEvent(Object source, UUID mappingId, String changeType, ItemMapping mapping) {
        super(source);
        this.mappingId = mappingId;
        this.changeType = changeType;
        this.mapping = mapping;
    }

    public UUID getMappingId() {
        return mappingId;
    }

    public String getChangeType() {
        return changeType;
    }

    public ItemMapping getMapping() {
        return mapping;
    }

    @Override
    public String toString() {
        return String.format("MappingChangedEvent[mappingId=%s, changeType=%s]", mappingId, changeType);
    }
}
