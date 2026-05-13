package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InteropElementTest {

    @Test
    void constructor_shouldRequireNonNullId() {
        assertThatThrownBy(() -> new InteropElement(null, "ID", "Name"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_shouldAcceptNullIdentifierAndName() {
        UUID id = UUID.randomUUID();
        InteropElement element = new InteropElement(id, null, null);

        assertThat(element.getId()).isEqualTo(id);
        assertThat(element.getIdentifier()).isNull();
        assertThat(element.getName()).isNull();
    }

    @Test
    void equals_shouldReturnTrueForSameId() {
        UUID id = UUID.randomUUID();
        InteropElement e1 = new InteropElement(id, "ID1", "Name1");
        InteropElement e2 = new InteropElement(id, "ID2", "Name2");

        assertThat(e1.equals(e2)).isTrue();
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test
    void equals_shouldReturnFalseForDifferentId() {
        InteropElement e1 = new InteropElement(UUID.randomUUID(), "ID1", "Name1");
        InteropElement e2 = new InteropElement(UUID.randomUUID(), "ID1", "Name1");

        assertThat(e1.equals(e2)).isFalse();
    }

    @Test
    void equals_shouldReturnTrueForSameInstance() {
        InteropElement e1 = new InteropElement(UUID.randomUUID(), "ID", "Name");
        assertThat(e1.equals(e1)).isTrue();
    }

    @Test
    void equals_shouldReturnFalseForNull() {
        InteropElement e1 = new InteropElement(UUID.randomUUID(), "ID", "Name");
        assertThat(e1.equals(null)).isFalse();
    }

    @Test
    void equals_shouldReturnFalseForDifferentClass() {
        InteropElement e1 = new InteropElement(UUID.randomUUID(), "ID", "Name");
        assertThat(e1.equals("not an element")).isFalse();
    }

    @Test
    void toString_shouldContainKeyFields() {
        UUID id = UUID.randomUUID();
        InteropElement element = new InteropElement(id, "ID", "Name");
        String str = element.toString();

        assertThat(str).contains(id.toString());
        assertThat(str).contains("ID");
        assertThat(str).contains("Name");
    }
}
