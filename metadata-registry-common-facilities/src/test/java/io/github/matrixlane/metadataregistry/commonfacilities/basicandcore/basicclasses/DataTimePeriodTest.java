package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DataTimePeriodTest {

    @Test
    void shouldConvertToStandard() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 12, 31, 23, 59);
        DataTimePeriod legacy = new DataTimePeriod(start, end);
        Datetime_Period standard = legacy.toStandard();
        assertThat(standard.getStartDateTime()).isEqualTo(start);
        assertThat(standard.getEndDateTime()).isEqualTo(end);
    }

    @Test
    void shouldCreateFromStandard() {
        Datetime_Period standard = new Datetime_Period(
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 12, 31, 23, 59));
        DataTimePeriod legacy = DataTimePeriod.fromStandard(standard);
        assertThat(legacy.getStartDateTime()).isEqualTo(standard.getStartDateTime());
        assertThat(legacy.getEndDateTime()).isEqualTo(standard.getEndDateTime());
    }

    @Test
    void fromStandardWithNullShouldReturnNull() {
        assertThat(DataTimePeriod.fromStandard(null)).isNull();
    }
}
