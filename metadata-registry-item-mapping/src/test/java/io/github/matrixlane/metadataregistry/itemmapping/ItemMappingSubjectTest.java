package io.github.matrixlane.metadataregistry.itemmapping;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ItemMappingSubjectTest {

    @Test
    void constructor_shouldCreateSubjectWithMappingItemAndVariableName() {
        ItemMapping mapping = new ItemMapping();
        Item item = mock(Item.class);

        ItemMappingSubject subject = new ItemMappingSubject(mapping, item, "year");

        assertThat(subject.getItemMapping()).isEqualTo(mapping);
        assertThat(subject.getItem()).isEqualTo(item);
        assertThat(subject.getMappingVariableName()).isEqualTo("year");
    }

    @Test
    void constructor_shouldAllowNullVariableName() {
        ItemMappingSubject subject = new ItemMappingSubject(
                new ItemMapping(), mock(Item.class), null);

        assertThat(subject.getMappingVariableName()).isNull();
    }

    @Test
    void constructor_shouldThrowWhenItemMappingIsNull() {
        assertThatThrownBy(() -> new ItemMappingSubject(null, mock(Item.class), "v"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("itemMapping must not be null");
    }

    @Test
    void constructor_shouldThrowWhenItemIsNull() {
        assertThatThrownBy(() -> new ItemMappingSubject(new ItemMapping(), null, "v"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("item must not be null");
    }

    @Test
    void setters_shouldUpdateItemAndVariableName() {
        ItemMappingSubject subject = new ItemMappingSubject(
                new ItemMapping(), mock(Item.class), "old");
        Item newItem = mock(Item.class);

        subject.setItem(newItem);
        subject.setMappingVariableName("new");

        assertThat(subject.getItem()).isEqualTo(newItem);
        assertThat(subject.getMappingVariableName()).isEqualTo("new");
    }

    @Test
    void equalsAndHashCode_shouldUseId() {
        ItemMappingSubject s1 = new ItemMappingSubject(new ItemMapping(), mock(Item.class), null);
        ItemMappingSubject s2 = new ItemMappingSubject(new ItemMapping(), mock(Item.class), null);

        // Both without IDs should not be equal
        assertThat(s1).isNotEqualTo(s2);

        // Same ID should be equal
        UUID id = UUID.randomUUID();
        s1.setId(id);
        s2.setId(id);
        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }
}
