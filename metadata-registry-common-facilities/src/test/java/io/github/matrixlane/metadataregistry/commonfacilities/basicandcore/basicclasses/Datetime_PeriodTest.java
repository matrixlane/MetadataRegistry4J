package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class Datetime_PeriodTest {

    @Test
    void shouldCreateWithStartAndEnd() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 12, 31, 23, 59);
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
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 12, 31, 23, 59);
        Datetime_Period dp = new Datetime_Period(start, end);

        assertThat(dp.contains(LocalDateTime.of(2024, 6, 15, 12, 0))).isTrue();
        assertThat(dp.contains(start)).isTrue();
        assertThat(dp.contains(end)).isTrue();
        assertThat(dp.contains(LocalDateTime.of(2023, 1, 1, 0, 0))).isFalse();
        assertThat(dp.contains(LocalDateTime.of(2025, 1, 1, 0, 0))).isFalse();
        assertThat(dp.contains(null)).isFalse();
    }

    @Test
    void containsWithOpenStart() {
        LocalDateTime end = LocalDateTime.of(2024, 12, 31, 23, 59);
        Datetime_Period dp = new Datetime_Period(null, end);
        assertThat(dp.contains(LocalDateTime.of(2024, 6, 15, 12, 0))).isTrue();
        assertThat(dp.contains(end)).isTrue();
        assertThat(dp.contains(LocalDateTime.of(2025, 1, 1, 0, 0))).isFalse();
    }

    @Test
    void containsWithOpenEnd() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        Datetime_Period dp = new Datetime_Period(start, null);
        assertThat(dp.contains(LocalDateTime.of(2024, 6, 15, 12, 0))).isTrue();
        assertThat(dp.contains(start)).isTrue();
        assertThat(dp.contains(LocalDateTime.of(2023, 1, 1, 0, 0))).isFalse();
    }

    @Test
    void overlapsShouldDetectOverlap() {
        Datetime_Period a = new Datetime_Period(
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 6, 30, 23, 59));
        Datetime_Period b = new Datetime_Period(
                LocalDateTime.of(2024, 3, 1, 0, 0),
                LocalDateTime.of(2024, 9, 30, 23, 59));
        assertThat(a.overlaps(b)).isTrue();
    }

    @Test
    void overlapsShouldDetectNoOverlap() {
        Datetime_Period a = new Datetime_Period(
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 3, 31, 23, 59));
        Datetime_Period b = new Datetime_Period(
                LocalDateTime.of(2024, 4, 1, 0, 0),
                LocalDateTime.of(2024, 6, 30, 23, 59));
        assertThat(a.overlaps(b)).isFalse();
    }

    @Test
    void overlapsWithNullShouldReturnFalse() {
        Datetime_Period a = new Datetime_Period(LocalDateTime.now(), null);
        assertThat(a.overlaps(null)).isFalse();
    }

    @Test
    void overlapsWithBothOpenStarts() {
        Datetime_Period a = new Datetime_Period(null, LocalDateTime.of(2024, 6, 30, 23, 59));
        Datetime_Period b = new Datetime_Period(null, LocalDateTime.of(2024, 12, 31, 23, 59));
        assertThat(a.overlaps(b)).isTrue();
    }
}
