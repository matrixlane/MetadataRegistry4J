package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalItemInfoTest {

    @Test
    void constructor_shouldRequireNonNullItemId() {
        assertThatThrownBy(() -> new LocalItemInfo(null, "Name", List.of("ID")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_shouldAcceptNullNameAndIdentifiers() {
        UUID id = UUID.randomUUID();
        LocalItemInfo item = new LocalItemInfo(id, null, null);

        assertThat(item.getItemId()).isEqualTo(id);
        assertThat(item.getName()).isEmpty();
        assertThat(item.getIdentifiers()).isEmpty();
    }

    @Test
    void constructor_shouldDefensivelyCopyIdentifiers() {
        UUID id = UUID.randomUUID();
        List<String> identifiers = new java.util.ArrayList<>(List.of("ID1", "ID2"));
        LocalItemInfo item = new LocalItemInfo(id, "Name", identifiers);

        identifiers.add("ID3");
        assertThat(item.getIdentifiers()).hasSize(2);
    }

    @Test
    void equals_shouldReturnTrueForSameItemId() {
        UUID id = UUID.randomUUID();
        LocalItemInfo i1 = new LocalItemInfo(id, "Name1", List.of("ID1"));
        LocalItemInfo i2 = new LocalItemInfo(id, "Name2", List.of("ID2"));

        assertThat(i1.equals(i2)).isTrue();
        assertThat(i1.hashCode()).isEqualTo(i2.hashCode());
    }

    @Test
    void equals_shouldReturnFalseForDifferentItemId() {
        LocalItemInfo i1 = new LocalItemInfo(UUID.randomUUID(), "Name", List.of("ID"));
        LocalItemInfo i2 = new LocalItemInfo(UUID.randomUUID(), "Name", List.of("ID"));

        assertThat(i1.equals(i2)).isFalse();
    }

    @Test
    void equals_shouldReturnTrueForSameInstance() {
        LocalItemInfo i1 = new LocalItemInfo(UUID.randomUUID(), "Name", List.of("ID"));
        assertThat(i1.equals(i1)).isTrue();
    }

    @Test
    void equals_shouldReturnFalseForNull() {
        LocalItemInfo i1 = new LocalItemInfo(UUID.randomUUID(), "Name", List.of("ID"));
        assertThat(i1.equals(null)).isFalse();
    }

    @Test
    void equals_shouldReturnFalseForDifferentClass() {
        LocalItemInfo i1 = new LocalItemInfo(UUID.randomUUID(), "Name", List.of("ID"));
        assertThat(i1.equals("not an item")).isFalse();
    }

    @Test
    void toString_shouldContainKeyFields() {
        UUID id = UUID.randomUUID();
        LocalItemInfo item = new LocalItemInfo(id, "Name", List.of("ID"));
        String str = item.toString();

        assertThat(str).contains(id.toString());
        assertThat(str).contains("Name");
        assertThat(str).contains("ID");
    }
}
