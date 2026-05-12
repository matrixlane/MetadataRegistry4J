package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DatetimeTest {

    private static final OffsetDateTime SAMPLE = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC);

    @Test
    void shouldCreateWithValue() {
        Datetime dt = Datetime.of(SAMPLE);
        assertThat(dt.getValue()).isEqualTo(SAMPLE);
    }

    @Test
    void shouldSetValue() {
        Datetime dt = new Datetime();
        dt.setValue(SAMPLE);
        assertThat(dt.getValue()).isEqualTo(SAMPLE);
    }

    @Test
    void toStringShouldReturnIsoString() {
        assertThat(Datetime.of(SAMPLE).toString()).isEqualTo("2024-01-15T10:30Z");
        assertThat(new Datetime().toString()).isNull();
    }

    @Test
    void equalsShouldBeBasedOnValue() {
        Datetime a = Datetime.of(SAMPLE);
        Datetime b = Datetime.of(SAMPLE);
        Datetime c = Datetime.of(SAMPLE.plusDays(1));
        Datetime nil = new Datetime();

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(nil);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isEqualTo(a);
    }

    @Test
    void hashCodeShouldBeConsistent() {
        Datetime a = Datetime.of(SAMPLE);
        Datetime b = Datetime.of(SAMPLE);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
