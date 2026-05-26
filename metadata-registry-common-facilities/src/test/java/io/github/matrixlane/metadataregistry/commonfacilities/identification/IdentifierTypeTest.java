package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierTypeTest {

    @Test
    void shouldHaveCorrectCodeValues() {
        assertThat(IdentifierType.INTERNAL.getCode()).isEqualTo("internal");
        assertThat(IdentifierType.URN.getCode()).isEqualTo("urn");
        assertThat(IdentifierType.OID.getCode()).isEqualTo("oid");
        assertThat(IdentifierType.LOINC.getCode()).isEqualTo("loinc");
        assertThat(IdentifierType.SNOMED.getCode()).isEqualTo("snomed");
        assertThat(IdentifierType.CUSTOM.getCode()).isEqualTo("custom");
    }

    @Test
    void shouldHaveDescriptions() {
        assertThat(IdentifierType.INTERNAL.getDescription()).isNotBlank();
        assertThat(IdentifierType.URN.getDescription()).isNotBlank();
    }

    @Test
    void shouldResolveFromCode() {
        assertThat(IdentifierType.fromCode("INTERNAL")).isEqualTo(IdentifierType.INTERNAL);
        assertThat(IdentifierType.fromCode("internal")).isEqualTo(IdentifierType.INTERNAL);
        assertThat(IdentifierType.fromCode("urn")).isEqualTo(IdentifierType.URN);
        assertThat(IdentifierType.fromCode("OID")).isEqualTo(IdentifierType.OID);
        assertThat(IdentifierType.fromCode("loinc")).isEqualTo(IdentifierType.LOINC);
        assertThat(IdentifierType.fromCode("snomed")).isEqualTo(IdentifierType.SNOMED);
        assertThat(IdentifierType.fromCode("custom")).isEqualTo(IdentifierType.CUSTOM);
    }

    @Test
    void shouldReturnCustomForUnknownCode() {
        assertThat(IdentifierType.fromCode("UNKNOWN")).isEqualTo(IdentifierType.CUSTOM);
        assertThat(IdentifierType.fromCode("xyz")).isEqualTo(IdentifierType.CUSTOM);
    }

    @Test
    void shouldReturnInternalForNullCode() {
        assertThat(IdentifierType.fromCode(null)).isEqualTo(IdentifierType.INTERNAL);
    }

    @Test
    void shouldHaveAllExpectedValues() {
        assertThat(IdentifierType.values()).hasSize(6);
    }
}
