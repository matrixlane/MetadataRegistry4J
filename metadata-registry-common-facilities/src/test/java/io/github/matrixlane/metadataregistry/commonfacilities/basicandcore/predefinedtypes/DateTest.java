package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DateTest {

    private static final LocalDate SAMPLE = LocalDate.of(2024, 1, 15);

    @Test
    void shouldCreateWithValue() {
        Date d = Date.of(SAMPLE);
        assertThat(d.getValue()).isEqualTo(SAMPLE);
    }

    @Test
    void shouldSetValue() {
        Date d = new Date();
        d.setValue(SAMPLE);
        assertThat(d.getValue()).isEqualTo(SAMPLE);
    }

    @Test
    void toStringShouldReturnIsoString() {
        assertThat(Date.of(SAMPLE).toString()).isEqualTo("2024-01-15");
        assertThat(new Date().toString()).isNull();
    }

    @Test
    void equalsShouldBeBasedOnValue() {
        Date a = Date.of(SAMPLE);
        Date b = Date.of(SAMPLE);
        Date c = Date.of(SAMPLE.plusDays(1));
        Date nil = new Date();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(nil);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isEqualTo(a);
    }

    @Test
    void hashCodeShouldBeConsistent() {
        Date a = Date.of(SAMPLE);
        Date b = Date.of(SAMPLE);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
