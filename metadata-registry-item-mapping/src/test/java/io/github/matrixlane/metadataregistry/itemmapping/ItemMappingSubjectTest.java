package io.github.matrixlane.metadataregistry.itemmapping;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemMappingSubjectTest {

    @Test
    void forItem_shouldCreateSubjectWithItemId() {
        UUID itemId = UUID.randomUUID();
        ItemMappingSubject subject = ItemMappingSubject.forItem(itemId);
        assertThat(subject.getItemId()).isEqualTo(itemId);
        assertThat(subject.getExternalUri()).isNull();
        assertThat(subject.isReferenceMutuallyExclusive()).isTrue();
    }

    @Test
    void forItem_shouldThrowWhenItemIdIsNull() {
        assertThatThrownBy(() -> ItemMappingSubject.forItem(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itemId must not be null");
    }

    @Test
    void forExternalUri_shouldCreateSubjectWithUri() {
        String uri = "https://example.org/fhir/Patient/123";
        ItemMappingSubject subject = ItemMappingSubject.forExternalUri(uri);
        assertThat(subject.getExternalUri()).isEqualTo(uri);
        assertThat(subject.getItemId()).isNull();
        assertThat(subject.isReferenceMutuallyExclusive()).isTrue();
    }

    @Test
    void forExternalUri_shouldThrowWhenUriIsNull() {
        assertThatThrownBy(() -> ItemMappingSubject.forExternalUri(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("externalUri must not be null or blank");
    }

    @Test
    void forExternalUri_shouldThrowWhenUriIsBlank() {
        assertThatThrownBy(() -> ItemMappingSubject.forExternalUri("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("externalUri must not be null or blank");
    }

    @Test
    void isReferenceMutuallyExclusive_shouldBeFalseWhenBothNull() {
        ItemMappingSubject subject = new ItemMappingSubject();
        assertThat(subject.isReferenceMutuallyExclusive()).isFalse();
    }

    @Test
    void isReferenceMutuallyExclusive_shouldBeFalseWhenBothSet() {
        ItemMappingSubject subject = new ItemMappingSubject();
        subject.setItemId(UUID.randomUUID());
        subject.setExternalUri("http://example.org");
        assertThat(subject.isReferenceMutuallyExclusive()).isFalse();
    }
}
