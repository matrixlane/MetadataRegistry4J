package io.github.matrixlane.metadataregistry.itemmapping;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemMappingTest {

    @Test
    void constructor_shouldCreateMappingWithRequiredFields() {
        UUID subjectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();

        ItemMappingSubject subject = ItemMappingSubject.forItem(subjectId);
        ItemMappingObject object = ItemMappingObject.forItem(objectId);
        ItemMapping mapping = new ItemMapping(subject, object, MappingType.EQUIVALENT);

        assertThat(mapping.getSubject()).isEqualTo(subject);
        assertThat(mapping.getObject()).isEqualTo(object);
        assertThat(mapping.getMappingType()).isEqualTo(MappingType.EQUIVALENT);
        assertThat(mapping.getCreatedAt()).isNotNull();
    }

    @Test
    void constructor_shouldThrowWhenSubjectIsNull() {
        UUID objectId = UUID.randomUUID();
        ItemMappingObject object = ItemMappingObject.forItem(objectId);

        assertThatThrownBy(() -> new ItemMapping(null, object, MappingType.EQUIVALENT))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_shouldThrowWhenObjectIsNull() {
        UUID subjectId = UUID.randomUUID();
        ItemMappingSubject subject = ItemMappingSubject.forItem(subjectId);

        assertThatThrownBy(() -> new ItemMapping(subject, null, MappingType.EQUIVALENT))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_shouldThrowWhenMappingTypeIsNull() {
        UUID subjectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();

        ItemMappingSubject subject = ItemMappingSubject.forItem(subjectId);
        ItemMappingObject object = ItemMappingObject.forItem(objectId);

        assertThatThrownBy(() -> new ItemMapping(subject, object, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_shouldAcceptDescription() {
        UUID subjectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();

        ItemMappingSubject subject = ItemMappingSubject.forItem(subjectId);
        ItemMappingObject object = ItemMappingObject.forItem(objectId);
        ItemMapping mapping = new ItemMapping(subject, object, MappingType.DERIVED,
                "Derived from source item for FHIR transformation");

        assertThat(mapping.getDescription()).isEqualTo(
                "Derived from source item for FHIR transformation");
    }

    @Test
    void equalsAndHashCode_shouldUseId() {
        UUID subjectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();

        ItemMappingSubject subject = ItemMappingSubject.forItem(subjectId);
        ItemMappingObject object = ItemMappingObject.forItem(objectId);
        ItemMapping mapping1 = new ItemMapping(subject, object, MappingType.EQUIVALENT);
        ItemMapping mapping2 = new ItemMapping(subject, object, MappingType.EQUIVALENT);

        // Different instances without IDs should not be equal
        assertThat(mapping1).isNotEqualTo(mapping2);
    }

    @Test
    void mappingTypeEnum_shouldHaveExpectedValues() {
        MappingType[] types = MappingType.values();
        assertThat(types).containsExactly(
                MappingType.EQUIVALENT,
                MappingType.DERIVED,
                MappingType.AGGREGATED,
                MappingType.SUBDIVIDED
        );
    }
}
