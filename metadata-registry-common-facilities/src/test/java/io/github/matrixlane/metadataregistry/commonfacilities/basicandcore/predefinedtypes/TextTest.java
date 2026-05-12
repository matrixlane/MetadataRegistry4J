package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextTest {

    @Test
    void shouldCreateWithValue() {
        Text t = Text.of("natural language");
        assertThat(t.getValue()).isEqualTo("natural language");
    }

    @Test
    void equalsAndHashCodeShouldBeBasedOnValue() {
        Text a = Text.of("t1");
        Text b = Text.of("t1");
        Text c = Text.of("t2");
        Text nil = new Text();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(nil);
        assertThat(a).isNotEqualTo(null);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toStringShouldReturnValue() {
        assertThat(Text.of("x").toString()).isEqualTo("x");
        assertThat(new Text().toString()).isNull();
    }
}
