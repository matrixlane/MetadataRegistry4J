package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationAuthorityIdentifierTest {

    @Test
    void shouldSetAllFields() {
        RegistrationAuthorityIdentifier rai = new RegistrationAuthorityIdentifier();
        rai.setInternationalCodeDesignator("ISO");
        rai.setOrganizationIdentifier("org-123");
        rai.setOrganizationPartIdentifier("part-456");
        rai.setOpiSource("source");

        assertThat(rai.getInternationalCodeDesignator()).isEqualTo("ISO");
        assertThat(rai.getOrganizationIdentifier()).isEqualTo("org-123");
        assertThat(rai.getOrganizationPartIdentifier()).isEqualTo("part-456");
        assertThat(rai.getOpiSource()).isEqualTo("source");
    }
}
