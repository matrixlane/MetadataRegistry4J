package io.github.matrixlane.metadataregistry.itemmapping.event;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MappingChangedEvent}.
 */
class MappingChangedEventTest {

    @Test
    void constructor_shouldStoreAllFields() {
        UUID mappingId = UUID.randomUUID();
        ItemMapping mapping = new ItemMapping(
                ItemMappingSubject.forItem(UUID.randomUUID()),
                ItemMappingObject.forItem(UUID.randomUUID()),
                MappingType.EQUIVALENT
        );

        MappingChangedEvent event = new MappingChangedEvent(
                this, mappingId, "CREATED", mapping);

        assertThat(event.getMappingId()).isEqualTo(mappingId);
        assertThat(event.getChangeType()).isEqualTo("CREATED");
        assertThat(event.getMapping()).isEqualTo(mapping);
        assertThat(event.getSource()).isEqualTo(this);
    }

    @Test
    void constructor_withNullMapping_shouldAllowNull() {
        UUID mappingId = UUID.randomUUID();

        MappingChangedEvent event = new MappingChangedEvent(
                this, mappingId, "DELETED", null);

        assertThat(event.getMappingId()).isEqualTo(mappingId);
        assertThat(event.getChangeType()).isEqualTo("DELETED");
        assertThat(event.getMapping()).isNull();
    }

    @Test
    void toString_shouldContainKeyInfo() {
        UUID mappingId = UUID.randomUUID();
        MappingChangedEvent event = new MappingChangedEvent(
                this, mappingId, "MODIFIED", null);

        String str = event.toString();

        assertThat(str).contains(mappingId.toString());
        assertThat(str).contains("MODIFIED");
    }
}
