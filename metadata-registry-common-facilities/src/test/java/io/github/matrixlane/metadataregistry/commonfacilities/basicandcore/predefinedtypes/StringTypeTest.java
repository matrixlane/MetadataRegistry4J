package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StringTypeTest {

    @Test
    void shouldCreateWithValue() {
        StringType st = StringType.of("hello");
        assertThat(st.getValue()).isEqualTo("hello");
    }

    @Test
    void shouldSetValue() {
        StringType st = new StringType();
        st.setValue("world");
        assertThat(st.getValue()).isEqualTo("world");
    }

    @Test
    void toStringShouldReturnValue() {
        assertThat(StringType.of("hello").toString()).isEqualTo("hello");
        assertThat(new StringType().toString()).isNull();
    }

    @Test
    void equalsShouldBeBasedOnValue() {
        StringType a = StringType.of("hello");
        StringType b = StringType.of("hello");
        StringType c = StringType.of("world");
        StringType nil = new StringType();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(nil);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isEqualTo(a);
    }

    @Test
    void hashCodeShouldBeConsistent() {
        StringType a = StringType.of("hello");
        StringType b = StringType.of("hello");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
