package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotationTest {

    @Test
    void shouldCreateWithValue() {
        Notation n = Notation.of("ISO 8601");
        assertThat(n.getValue()).isEqualTo("ISO 8601");
    }

    @Test
    void toStringShouldReturnValue() {
        assertThat(Notation.of("n").toString()).isEqualTo("n");
        assertThat(new Notation().toString()).isNull();
    }
}
