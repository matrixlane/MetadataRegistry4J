package io.github.matrixlane.metadataregistry.itemmapping;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ItemMappingObjectTest {

    @Test
    void constructor_shouldCreateObjectWithMappingItemAndVariableName() {
        ItemMapping mapping = new ItemMapping();
        Item item = mock(Item.class);

        ItemMappingObject object = new ItemMappingObject(mapping, item, "isodate");

        assertThat(object.getItemMapping()).isEqualTo(mapping);
        assertThat(object.getItem()).isEqualTo(item);
        assertThat(object.getMappingVariableName()).isEqualTo("isodate");
    }

    @Test
    void constructor_shouldAllowNullVariableName() {
        ItemMappingObject object = new ItemMappingObject(
                new ItemMapping(), mock(Item.class), null);

        assertThat(object.getMappingVariableName()).isNull();
    }

    @Test
    void constructor_shouldThrowWhenItemMappingIsNull() {
        assertThatThrownBy(() -> new ItemMappingObject(null, mock(Item.class), "v"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("itemMapping must not be null");
    }

    @Test
    void constructor_shouldThrowWhenItemIsNull() {
        assertThatThrownBy(() -> new ItemMappingObject(new ItemMapping(), null, "v"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("item must not be null");
    }

    @Test
    void setters_shouldUpdateItemAndVariableName() {
        ItemMappingObject object = new ItemMappingObject(
                new ItemMapping(), mock(Item.class), "old");
        Item newItem = mock(Item.class);

        object.setItem(newItem);
        object.setMappingVariableName("new");

        assertThat(object.getItem()).isEqualTo(newItem);
        assertThat(object.getMappingVariableName()).isEqualTo("new");
    }

    @Test
    void equalsAndHashCode_shouldUseId() {
        ItemMappingObject o1 = new ItemMappingObject(new ItemMapping(), mock(Item.class), null);
        ItemMappingObject o2 = new ItemMappingObject(new ItemMapping(), mock(Item.class), null);

        // Both without IDs should not be equal
        assertThat(o1).isNotEqualTo(o2);

        // Same ID should be equal
        UUID id = UUID.randomUUID();
        o1.setId(id);
        o2.setId(id);
        assertThat(o1).isEqualTo(o2);
        assertThat(o1.hashCode()).isEqualTo(o2.hashCode());
    }
}
