package io.github.matrixlane.metadataregistry.ext.registration;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationAuthorityExtensionTest {

    @Test
    void shouldDefaultToNotPrimary() {
        RegistrationAuthorityExtension extension = new RegistrationAuthorityExtension();
        assertThat(extension.isPrimary()).isFalse();
        assertThat(extension.getId()).isNull();
    }

    @Test
    void shouldCreateWithValueKeyAndPrimaryFlag() {
        UUID raId = UUID.randomUUID();
        RegistrationAuthorityExtension extension = new RegistrationAuthorityExtension(raId, true);
        assertThat(extension.getId()).isEqualTo(raId);
        assertThat(extension.isPrimary()).isTrue();
        extension.setPrimary(false);
        assertThat(extension.isPrimary()).isFalse();
    }
}
