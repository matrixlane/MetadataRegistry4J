package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Compatibility alias for {@link Datetime_Period}.
 * <p>
 * DEVIATION: Class name should be {@code Datetime_Period} per ISO/IEC 11179-3:2023, 6.3.9.
 * Retained for backward compatibility with existing JPA embeddable usages.
 *
 * @deprecated Use {@link Datetime_Period} instead.
 */
@StandardReference(value = "ISO/IEC 11179-3:2023, 6.3.9",
        deviation = "Class name deviates from standard (should be Datetime_Period). Retained as compatibility alias.")
@Deprecated(since = "0.1.0", forRemoval = false)
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class DataTimePeriod {

    private LocalDateTime startDateTime;

    private LocalDateTime endDateTime;

    public DataTimePeriod(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
    }

    /**
     * Converts this compatibility instance to the canonical {@link Datetime_Period}.
     */
    public Datetime_Period toStandard() {
        return new Datetime_Period(startDateTime, endDateTime);
    }

    /**
     * Creates a compatibility instance from the canonical {@link Datetime_Period}.
     */
    public static DataTimePeriod fromStandard(Datetime_Period period) {
        if (period == null) {
            return null;
        }
        return new DataTimePeriod(period.getStartDateTime(), period.getEndDateTime());
    }
}
