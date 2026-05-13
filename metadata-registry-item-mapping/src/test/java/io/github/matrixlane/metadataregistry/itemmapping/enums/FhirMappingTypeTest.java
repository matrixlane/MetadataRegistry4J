package io.github.matrixlane.metadataregistry.itemmapping.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FhirMappingTypeTest {

    @Test
    void enumValues_shouldContainOneToOneAndOneToMany() {
        assertThat(FhirMappingType.values())
                .containsExactly(FhirMappingType.ONE_TO_ONE, FhirMappingType.ONE_TO_MANY);
    }

    @Test
    void valueOf_shouldReturnCorrectEnum() {
        assertThat(FhirMappingType.valueOf("ONE_TO_ONE"))
                .isEqualTo(FhirMappingType.ONE_TO_ONE);
        assertThat(FhirMappingType.valueOf("ONE_TO_MANY"))
                .isEqualTo(FhirMappingType.ONE_TO_MANY);
    }
}
