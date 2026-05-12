package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailAddressTest {

    @Test
    void shouldCreateWithValue() {
        EmailAddress ea = EmailAddress.of("test@example.com");
        assertThat(ea.getValue()).isEqualTo("test@example.com");
    }

    @Test
    void toStringShouldReturnValue() {
        assertThat(EmailAddress.of("a@b.com").toString()).isEqualTo("a@b.com");
        assertThat(new EmailAddress().toString()).isNull();
    }
}
