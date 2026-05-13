package io.github.matrixlane.metadataregistry.itemmapping.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FhirMappingEquivalenceTest {

    @ParameterizedTest
    @CsvSource({
            "equivalent, EQUIVALENT",
            "equal, EQUAL",
            "wider, WIDER",
            "narrower, NARROWER",
            "subsumes, SUBSUMES",
            "specializes, SPECIALIZES",
            "relatedto, RELATEDTO",
            "inexact, INEXACT",
            "unmatched, UNMATCHED",
            "disjoint, DISJOINT"
    })
    void fromCode_shouldParseAllValidCodes(String code, FhirMappingEquivalence expected) {
        assertThat(FhirMappingEquivalence.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "EQUIVALENT, equivalent",
            "EQUAL, equal",
            "WIDER, wider",
            "NARROWER, narrower",
            "RELATEDTO, relatedto"
    })
    void toCode_shouldReturnLowercase(FhirMappingEquivalence equivalence, String expected) {
        assertThat(equivalence.toCode()).isEqualTo(expected);
    }

    @Test
    void fromCode_null_shouldThrow() {
        assertThatThrownBy(() -> FhirMappingEquivalence.fromCode(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void fromCode_blank_shouldThrow() {
        assertThatThrownBy(() -> FhirMappingEquivalence.fromCode("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void fromCode_unknown_shouldThrow() {
        assertThatThrownBy(() -> FhirMappingEquivalence.fromCode("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromCode_caseInsensitive_shouldWork() {
        assertThat(FhirMappingEquivalence.fromCode("Equivalent")).isEqualTo(FhirMappingEquivalence.EQUIVALENT);
        assertThat(FhirMappingEquivalence.fromCode("WIDER")).isEqualTo(FhirMappingEquivalence.WIDER);
    }
}
