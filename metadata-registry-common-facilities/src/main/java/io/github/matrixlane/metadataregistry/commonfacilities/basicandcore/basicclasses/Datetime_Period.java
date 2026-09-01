package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * ISO/IEC 11179-3:2023, 6.3.10 — Datetime_Period
 * <p>
 * A period of time defined by a start and an end datetime.
 * <p>
 * start/end 的类型为 6.2.3 Datetime,含时区(ISO 8601 basic format),
 * 以 {@link java.time.OffsetDateTime} 对应。
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.3.10")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class Datetime_Period {

    private OffsetDateTime startDateTime;

    private OffsetDateTime endDateTime;

    public Datetime_Period(OffsetDateTime startDateTime, OffsetDateTime endDateTime) {
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
    }

    /**
     * Returns true if this period contains the given point in time.
     */
    public boolean contains(OffsetDateTime point) {
        if (point == null) {
            return false;
        }
        boolean afterStart = startDateTime == null || !point.isBefore(startDateTime);
        boolean beforeEnd = endDateTime == null || !point.isAfter(endDateTime);
        return afterStart && beforeEnd;
    }

    /**
     * Returns true if this period overlaps with another period.
     */
    public boolean overlaps(Datetime_Period other) {
        if (other == null) {
            return false;
        }
        if (startDateTime == null && other.startDateTime == null) {
            return true;
        }
        if (startDateTime == null || other.startDateTime == null) {
            return !(endDateTime != null && other.endDateTime != null
                    && endDateTime.isBefore(other.startDateTime != null ? other.startDateTime : endDateTime.plusSeconds(1))
                    && other.endDateTime.isBefore(startDateTime != null ? startDateTime : other.endDateTime.plusSeconds(1)));
        }
        boolean thisBeforeOther = endDateTime != null && endDateTime.isBefore(other.startDateTime);
        boolean otherBeforeThis = other.endDateTime != null && other.endDateTime.isBefore(startDateTime);
        return !thisBeforeOther && !otherBeforeThis;
    }
}
