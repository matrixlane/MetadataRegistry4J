package io.github.matrixlane.metadataregistry.itemmapping.entity;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingSchemeStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MappingSchemeTest {

    @Test
    void noArgsConstructor_shouldCreateEmptyScheme() {
        MappingScheme scheme = new MappingScheme();
        assertThat(scheme).isNotNull();
    }

    @Test
    void settersAndGetters_shouldWork() {
        MappingScheme scheme = new MappingScheme();
        UUID id = UUID.randomUUID();
        UUID registryId = UUID.randomUUID();

        scheme.setId(id);
        scheme.setName("Test Scheme");
        scheme.setDescription("A test mapping scheme");
        scheme.setSourceUri("http://source");
        scheme.setTargetUri("http://target");
        scheme.setVersion("1.0.0");
        scheme.setStatus(MappingSchemeStatus.PUBLISHED);

        assertThat(scheme.getId()).isEqualTo(id);
        assertThat(scheme.getName()).isEqualTo("Test Scheme");
        assertThat(scheme.getDescription()).isEqualTo("A test mapping scheme");
        assertThat(scheme.getSourceUri()).isEqualTo("http://source");
        assertThat(scheme.getTargetUri()).isEqualTo("http://target");
        assertThat(scheme.getVersion()).isEqualTo("1.0.0");
        assertThat(scheme.getStatus()).isEqualTo(MappingSchemeStatus.PUBLISHED);
    }

    @Test
    void equals_shouldReturnTrueForSameInstance() {
        MappingScheme scheme = new MappingScheme();
        assertThat(scheme.equals(scheme)).isTrue();
    }

    @Test
    void equals_shouldReturnFalseForNull() {
        MappingScheme scheme = new MappingScheme();
        assertThat(scheme.equals(null)).isFalse();
    }

    @Test
    void equals_shouldReturnFalseForDifferentClass() {
        MappingScheme scheme = new MappingScheme();
        assertThat(scheme.equals("not a scheme")).isFalse();
    }

    @Test
    void hashCode_shouldBeConsistent() {
        MappingScheme scheme = new MappingScheme();
        int hash1 = scheme.hashCode();
        int hash2 = scheme.hashCode();
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void toString_shouldContainKeyFields() {
        MappingScheme scheme = new MappingScheme();
        scheme.setName("Test");
        String str = scheme.toString();
        assertThat(str).contains("Test");
    }
}
