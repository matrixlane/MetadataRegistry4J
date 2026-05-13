package io.github.matrixlane.metadataregistry.itemmapping.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumValueTest {

    @Test
    void interopMappingStatus_shouldHaveExpectedValues() {
        InteropMappingStatus[] values = InteropMappingStatus.values();
        assertThat(values).containsExactly(
                InteropMappingStatus.AUTO_MATCHED,
                InteropMappingStatus.PENDING,
                InteropMappingStatus.CONFIRMED,
                InteropMappingStatus.REJECTED,
                InteropMappingStatus.UNMATCHED
        );
    }

    @Test
    void interopMappingStatus_valueOf_shouldWork() {
        assertThat(InteropMappingStatus.valueOf("PENDING"))
                .isEqualTo(InteropMappingStatus.PENDING);
        assertThat(InteropMappingStatus.valueOf("AUTO_MATCHED"))
                .isEqualTo(InteropMappingStatus.AUTO_MATCHED);
        assertThat(InteropMappingStatus.valueOf("CONFIRMED"))
                .isEqualTo(InteropMappingStatus.CONFIRMED);
        assertThat(InteropMappingStatus.valueOf("REJECTED"))
                .isEqualTo(InteropMappingStatus.REJECTED);
    }

    @Test
    void mappingSchemeStatus_shouldHaveExpectedValues() {
        MappingSchemeStatus[] values = MappingSchemeStatus.values();
        assertThat(values).containsExactly(
                MappingSchemeStatus.DRAFT,
                MappingSchemeStatus.PUBLISHED,
                MappingSchemeStatus.ARCHIVED
        );
    }

    @Test
    void mappingSchemeStatus_valueOf_shouldWork() {
        assertThat(MappingSchemeStatus.valueOf("DRAFT"))
                .isEqualTo(MappingSchemeStatus.DRAFT);
        assertThat(MappingSchemeStatus.valueOf("PUBLISHED"))
                .isEqualTo(MappingSchemeStatus.PUBLISHED);
        assertThat(MappingSchemeStatus.valueOf("ARCHIVED"))
                .isEqualTo(MappingSchemeStatus.ARCHIVED);
    }

    @Test
    void mappingType_shouldHaveExpectedValues() {
        MappingType[] values = MappingType.values();
        assertThat(values).containsExactly(
                MappingType.EQUIVALENT,
                MappingType.DERIVED,
                MappingType.AGGREGATED,
                MappingType.SUBDIVIDED
        );
    }

    @Test
    void mappingType_valueOf_shouldWork() {
        assertThat(MappingType.valueOf("EQUIVALENT"))
                .isEqualTo(MappingType.EQUIVALENT);
        assertThat(MappingType.valueOf("DERIVED"))
                .isEqualTo(MappingType.DERIVED);
        assertThat(MappingType.valueOf("AGGREGATED"))
                .isEqualTo(MappingType.AGGREGATED);
        assertThat(MappingType.valueOf("SUBDIVIDED"))
                .isEqualTo(MappingType.SUBDIVIDED);
    }
}
