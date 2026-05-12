package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * ISO/IEC 11179-3:2023, 6.2.3 — Datetime
 * <p>
 * A point in time represented as a date and time with timezone offset,
 * conforming to ISO 8601.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2.3")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Datetime implements IValueType<OffsetDateTime> {

    private OffsetDateTime value;

    public static Datetime of(OffsetDateTime value) {
        return new Datetime(value);
    }

    @Override
    public String toString() {
        return value == null ? null : value.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Datetime other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
