package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BooleanTypeTest {

    @Test
    void shouldCreateWithValue() {
        BooleanType bt = BooleanType.of(true);
        assertThat(bt.getValue()).isTrue();
    }

    @Test
    void shouldSetValue() {
        BooleanType bt = new BooleanType();
        bt.setValue(false);
        assertThat(bt.getValue()).isFalse();
    }

    @Test
    void toStringShouldReturnValue() {
        assertThat(BooleanType.of(true).toString()).isEqualTo("true");
        assertThat(BooleanType.of(false).toString()).isEqualTo("false");
        assertThat(new BooleanType().toString()).isNull();
    }

    @Test
    void equalsShouldBeBasedOnValue() {
        BooleanType a = BooleanType.of(true);
        BooleanType b = BooleanType.of(true);
        BooleanType c = BooleanType.of(false);
        BooleanType nil = new BooleanType();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(nil);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("true");
        assertThat(a).isEqualTo(a);
    }

    @Test
    void hashCodeShouldBeConsistent() {
        BooleanType a = BooleanType.of(true);
        BooleanType b = BooleanType.of(true);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
