package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberTest {

    @Test
    void shouldCreateWithValue() {
        PhoneNumber pn = PhoneNumber.of("+86-138-0000-0000");
        assertThat(pn.getValue()).isEqualTo("+86-138-0000-0000");
    }

    @Test
    void toStringShouldReturnValue() {
        assertThat(PhoneNumber.of("123").toString()).isEqualTo("123");
        assertThat(new PhoneNumber().toString()).isNull();
    }
}
