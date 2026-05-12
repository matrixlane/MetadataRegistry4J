package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntegerTypeTest {

    @Test
    void shouldCreateWithValue() {
        IntegerType it = IntegerType.of(42L);
        assertThat(it.getValue()).isEqualTo(42L);
    }

    @Test
    void shouldSetValue() {
        IntegerType it = new IntegerType();
        it.setValue(99L);
        assertThat(it.getValue()).isEqualTo(99L);
    }

    @Test
    void toStringShouldReturnValue() {
        assertThat(IntegerType.of(42L).toString()).isEqualTo("42");
        assertThat(new IntegerType().toString()).isNull();
    }

    @Test
    void equalsShouldBeBasedOnValue() {
        IntegerType a = IntegerType.of(42L);
        IntegerType b = IntegerType.of(42L);
        IntegerType c = IntegerType.of(7L);
        IntegerType nil = new IntegerType();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(nil);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isEqualTo(a);
    }

    @Test
    void hashCodeShouldBeConsistent() {
        IntegerType a = IntegerType.of(42L);
        IntegerType b = IntegerType.of(42L);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
