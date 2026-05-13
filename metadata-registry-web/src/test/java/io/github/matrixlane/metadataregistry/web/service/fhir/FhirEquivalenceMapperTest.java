package io.github.matrixlane.metadataregistry.web.service.fhir;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import org.hl7.fhir.r4.model.Enumerations;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link FhirEquivalenceMapper}.
 *
 * <p>Covers all four MappingType → FHIR equivalence mappings,
 * edge cases, and utility methods (WI-011-2-2 AC: 等价类型映射).
 */
class FhirEquivalenceMapperTest {

    // ========================================================================
    // Core Mapping Tests
    // ========================================================================

    @Test
    void toFhirEquivalence_equivalent_returnsEquivalent() {
        assertThat(FhirEquivalenceMapper.toFhirEquivalence(MappingType.EQUIVALENT))
                .isEqualTo(Enumerations.ConceptMapEquivalence.EQUIVALENT);
    }

    @Test
    void toFhirEquivalence_derived_returnsWider() {
        assertThat(FhirEquivalenceMapper.toFhirEquivalence(MappingType.DERIVED))
                .isEqualTo(Enumerations.ConceptMapEquivalence.WIDER);
    }

    @Test
    void toFhirEquivalence_aggregated_returnsNarrower() {
        assertThat(FhirEquivalenceMapper.toFhirEquivalence(MappingType.AGGREGATED))
                .isEqualTo(Enumerations.ConceptMapEquivalence.NARROWER);
    }

    @Test
    void toFhirEquivalence_subdivided_returnsRelatedto() {
        assertThat(FhirEquivalenceMapper.toFhirEquivalence(MappingType.SUBDIVIDED))
                .isEqualTo(Enumerations.ConceptMapEquivalence.RELATEDTO);
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Test
    void toFhirEquivalence_null_throws() {
        assertThatThrownBy(() -> FhirEquivalenceMapper.toFhirEquivalence(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No FHIR equivalence mapping defined for: null");
    }

    // ========================================================================
    // Code String Tests
    // ========================================================================

    @Test
    void toFhirEquivalenceCode_equivalent_returnsEquivalentString() {
        assertThat(FhirEquivalenceMapper.toFhirEquivalenceCode(MappingType.EQUIVALENT))
                .isEqualTo("equivalent");
    }

    @Test
    void toFhirEquivalenceCode_derived_returnsWiderString() {
        assertThat(FhirEquivalenceMapper.toFhirEquivalenceCode(MappingType.DERIVED))
                .isEqualTo("wider");
    }

    @Test
    void toFhirEquivalenceCode_aggregated_returnsNarrowerString() {
        assertThat(FhirEquivalenceMapper.toFhirEquivalenceCode(MappingType.AGGREGATED))
                .isEqualTo("narrower");
    }

    @Test
    void toFhirEquivalenceCode_subdivided_returnsRelatedtoString() {
        assertThat(FhirEquivalenceMapper.toFhirEquivalenceCode(MappingType.SUBDIVIDED))
                .isEqualTo("relatedto");
    }

    // ========================================================================
    // Utility Method Tests
    // ========================================================================

    @Test
    void getEquivalenceSystem_returnsCanonicalUri() {
        assertThat(FhirEquivalenceMapper.getEquivalenceSystem())
                .isEqualTo("http://hl7.org/fhir/concept-map-equivalence");
    }

    @Test
    void mappingCount_returnsFour() {
        assertThat(FhirEquivalenceMapper.mappingCount()).isEqualTo(4);
    }
}
