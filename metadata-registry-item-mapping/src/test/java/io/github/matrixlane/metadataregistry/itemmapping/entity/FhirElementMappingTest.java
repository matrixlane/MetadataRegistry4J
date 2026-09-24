package io.github.matrixlane.metadataregistry.itemmapping.entity;

import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingEquivalence;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FhirElementMappingTest {

    @Test
    void constructor_oneToOne_shouldSetDefaults() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT
        );

        assertThat(mapping.getLocalItemId()).isEqualTo(itemId);
        assertThat(mapping.getFhirPath()).isEqualTo("Patient.name.given");
        assertThat(mapping.getFhirResourceType()).isEqualTo("Patient");
        assertThat(mapping.getFhirEquivalence()).isEqualTo(FhirMappingEquivalence.EQUIVALENT);
        assertThat(mapping.getMappingType()).isEqualTo(FhirMappingType.ONE_TO_ONE);
        assertThat(mapping.getMappingVariable()).isNull();
        assertThat(mapping.getCreatedAt()).isNotNull();
        assertThat(mapping.getUpdatedAt()).isNotNull();
        assertThat(mapping.getVersion()).isEqualTo(0L);
        assertThat(mapping.isOneToMany()).isFalse();
    }

    @Test
    void constructor_oneToMany_shouldStoreVariable() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.name.family", "Patient",
                FhirMappingEquivalence.EQUIVALENT,
                FhirMappingType.ONE_TO_MANY,
                "family", "Family name"
        );

        assertThat(mapping.getMappingType()).isEqualTo(FhirMappingType.ONE_TO_MANY);
        assertThat(mapping.getMappingVariable()).isEqualTo("family");
        assertThat(mapping.isOneToMany()).isTrue();
        assertThat(mapping.getDisplayName()).isEqualTo("family → Patient.name.family");
    }

    @Test
    void constructor_nullLocalItemId_shouldThrow() {
        assertThatThrownBy(() -> new FhirElementMapping(
                null, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("localItemId");
    }

    @Test
    void constructor_blankFhirPath_shouldThrow() {
        UUID itemId = UUID.randomUUID();
        assertThatThrownBy(() -> new FhirElementMapping(
                itemId, "", "Patient", FhirMappingEquivalence.EQUIVALENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fhirPath");
    }

    @Test
    void constructor_nullFhirPath_shouldThrow() {
        UUID itemId = UUID.randomUUID();
        assertThatThrownBy(() -> new FhirElementMapping(
                itemId, null, "Patient", FhirMappingEquivalence.EQUIVALENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fhirPath");
    }

    @Test
    void constructor_blankResourceType_shouldThrow() {
        UUID itemId = UUID.randomUUID();
        assertThatThrownBy(() -> new FhirElementMapping(
                itemId, "Patient.name.given", "  ", FhirMappingEquivalence.EQUIVALENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fhirResourceType");
    }

    @Test
    void constructor_nullEquivalence_shouldThrow() {
        UUID itemId = UUID.randomUUID();
        assertThatThrownBy(() -> new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fhirEquivalence");
    }

    @Test
    void update_shouldModifyFields() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT
        );

        UUID updater = UUID.randomUUID();
        mapping.update("Patient.name.family", FhirMappingEquivalence.WIDER,
                FhirMappingType.ONE_TO_MANY, "family", "Updated desc", updater);

        assertThat(mapping.getFhirPath()).isEqualTo("Patient.name.family");
        assertThat(mapping.getFhirEquivalence()).isEqualTo(FhirMappingEquivalence.WIDER);
        assertThat(mapping.getMappingType()).isEqualTo(FhirMappingType.ONE_TO_MANY);
        assertThat(mapping.getMappingVariable()).isEqualTo("family");
        assertThat(mapping.getDescription()).isEqualTo("Updated desc");
        assertThat(mapping.getUpdatedBy()).isEqualTo(updater);
        assertThat(mapping.getUpdatedAt()).isAfterOrEqualTo(mapping.getCreatedAt());
    }

    @Test
    void update_withNulls_shouldKeepCurrentValues() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT
        );

        mapping.update(null, null, null, null, null, null);

        assertThat(mapping.getFhirPath()).isEqualTo("Patient.name.given");
        assertThat(mapping.getFhirEquivalence()).isEqualTo(FhirMappingEquivalence.EQUIVALENT);
        assertThat(mapping.getMappingType()).isEqualTo(FhirMappingType.ONE_TO_ONE);
    }

    @Test
    void update_blankMappingVariable_shouldSetNull() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient",
                FhirMappingEquivalence.EQUIVALENT,
                FhirMappingType.ONE_TO_MANY, "var", "desc"
        );

        mapping.update(null, null, null, "", null, null);
        assertThat(mapping.getMappingVariable()).isNull();
    }

    @Test
    void displayName_oneToOne_shouldReturnPathOnly() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.birthDate", "Patient", FhirMappingEquivalence.EQUAL
        );
        assertThat(mapping.getDisplayName()).isEqualTo("Patient.birthDate");
    }

    @Test
    void equals_sameId_shouldBeEqual() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping a = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT
        );
        FhirElementMapping b = new FhirElementMapping(
                itemId, "Patient.name.family", "Patient", FhirMappingEquivalence.WIDER
        );
        a.setId(UUID.randomUUID());
        b.setId(a.getId());

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void equals_differentId_shouldNotBeEqual() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping a = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT
        );
        FhirElementMapping b = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT
        );
        a.setId(UUID.randomUUID());
        b.setId(UUID.randomUUID());

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void toString_shouldContainKeyFields() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT
        );

        assertThat(mapping.toString())
                .contains("localItemId")
                .contains("fhirPath")
                .contains("Patient");
    }
}
