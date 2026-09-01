package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class Datetime_PeriodTest {

    @Test
    void shouldCreateWithStartAndEnd() {
        OffsetDateTime start = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2024, 12, 31, 23, 59, 0, 0, ZoneOffset.UTC);
        Datetime_Period dp = new Datetime_Period(start, end);
        assertThat(dp.getStartDateTime()).isEqualTo(start);
        assertThat(dp.getEndDateTime()).isEqualTo(end);
    }

    @Test
    void noArgConstructorShouldInitialize() {
        Datetime_Period dp = new Datetime_Period();
        assertThat(dp.getStartDateTime()).isNull();
        assertThat(dp.getEndDateTime()).isNull();
    }

    @Test
    void containsShouldWorkForBoundedPeriod() {
        OffsetDateTime start = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2024, 12, 31, 23, 59, 0, 0, ZoneOffset.UTC);
        Datetime_Period dp = new Datetime_Period(start, end);

        assertThat(dp.contains(OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC))).isTrue();
        assertThat(dp.contains(start)).isTrue();
        assertThat(dp.contains(end)).isTrue();
        assertThat(dp.contains(OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))).isFalse();
        assertThat(dp.contains(OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))).isFalse();
        assertThat(dp.contains(null)).isFalse();
    }

    @Test
    void containsWithOpenStart() {
        OffsetDateTime end = OffsetDateTime.of(2024, 12, 31, 23, 59, 0, 0, ZoneOffset.UTC);
        Datetime_Period dp = new Datetime_Period(null, end);
        assertThat(dp.contains(OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC))).isTrue();
        assertThat(dp.contains(end)).isTrue();
        assertThat(dp.contains(OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))).isFalse();
    }

    @Test
    void containsWithOpenEnd() {
        OffsetDateTime start = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        Datetime_Period dp = new Datetime_Period(start, null);
        assertThat(dp.contains(OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC))).isTrue();
        assertThat(dp.contains(start)).isTrue();
        assertThat(dp.contains(OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))).isFalse();
    }

    @Test
    void overlapsShouldDetectOverlap() {
        Datetime_Period a = new Datetime_Period(
                OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 6, 30, 23, 59, 0, 0, ZoneOffset.UTC));
        Datetime_Period b = new Datetime_Period(
                OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 9, 30, 23, 59, 0, 0, ZoneOffset.UTC));
        assertThat(a.overlaps(b)).isTrue();
    }

    @Test
    void overlapsShouldDetectNoOverlap() {
        Datetime_Period a = new Datetime_Period(
                OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 3, 31, 23, 59, 0, 0, ZoneOffset.UTC));
        Datetime_Period b = new Datetime_Period(
                OffsetDateTime.of(2024, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2024, 6, 30, 23, 59, 0, 0, ZoneOffset.UTC));
        assertThat(a.overlaps(b)).isFalse();
    }

    @Test
    void overlapsWithNullShouldReturnFalse() {
        Datetime_Period a = new Datetime_Period(OffsetDateTime.now(), null);
        assertThat(a.overlaps(null)).isFalse();
    }

    @Test
    void overlapsWithBothOpenStarts() {
        Datetime_Period a = new Datetime_Period(null, OffsetDateTime.of(2024, 6, 30, 23, 59, 0, 0, ZoneOffset.UTC));
        Datetime_Period b = new Datetime_Period(null, OffsetDateTime.of(2024, 12, 31, 23, 59, 0, 0, ZoneOffset.UTC));
        assertThat(a.overlaps(b)).isTrue();
    }
}
