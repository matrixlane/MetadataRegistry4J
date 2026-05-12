package io.github.matrixlane.metadataregistry.itemmapping;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemMappingObjectTest {

    @Test
    void forItem_shouldCreateObjectWithItemId() {
        UUID itemId = UUID.randomUUID();
        ItemMappingObject object = ItemMappingObject.forItem(itemId);
        assertThat(object.getItemId()).isEqualTo(itemId);
        assertThat(object.getExternalUri()).isNull();
        assertThat(object.isReferenceMutuallyExclusive()).isTrue();
    }

    @Test
    void forItem_shouldThrowWhenItemIdIsNull() {
        assertThatThrownBy(() -> ItemMappingObject.forItem(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itemId must not be null");
    }

    @Test
    void forExternalUri_shouldCreateObjectWithUri() {
        String uri = "https://example.org/fhir/ValueSet/456";
        ItemMappingObject object = ItemMappingObject.forExternalUri(uri);
        assertThat(object.getExternalUri()).isEqualTo(uri);
        assertThat(object.getItemId()).isNull();
        assertThat(object.isReferenceMutuallyExclusive()).isTrue();
    }

    @Test
    void forExternalUri_shouldThrowWhenUriIsNull() {
        assertThatThrownBy(() -> ItemMappingObject.forExternalUri(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("externalUri must not be null or blank");
    }

    @Test
    void isReferenceMutuallyExclusive_shouldBeFalseWhenBothNull() {
        ItemMappingObject object = new ItemMappingObject();
        assertThat(object.isReferenceMutuallyExclusive()).isFalse();
    }
}
