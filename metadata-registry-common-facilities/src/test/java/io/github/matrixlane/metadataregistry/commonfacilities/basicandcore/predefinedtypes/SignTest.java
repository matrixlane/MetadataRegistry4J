package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignTest {

    @Test
    void shouldCreateWithValue() {
        Sign s = Sign.of("alpha");
        assertThat(s.getValue()).isEqualTo("alpha");
    }

    @Test
    void equalsAndHashCodeShouldBeBasedOnValue() {
        Sign a = Sign.of("s1");
        Sign b = Sign.of("s1");
        Sign c = Sign.of("s2");
        Sign nil = new Sign();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(nil);
        assertThat(a).isNotEqualTo(null);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toStringShouldReturnValue() {
        assertThat(Sign.of("x").toString()).isEqualTo("x");
        assertThat(new Sign().toString()).isNull();
    }
}
