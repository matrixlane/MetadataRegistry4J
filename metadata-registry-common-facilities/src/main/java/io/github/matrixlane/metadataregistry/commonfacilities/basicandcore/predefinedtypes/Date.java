package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Objects;

/**
 * ISO/IEC 11179-3:2023, 6.2.2 — Date
 * <p>
 * A calendar date represented as year, month, and day,
 * conforming to ISO 8601.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2.2")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Date implements IValueType<LocalDate> {

    private LocalDate value;

    public static Date of(LocalDate value) {
        return new Date(value);
    }

    @Override
    public String toString() {
        return value == null ? null : value.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Date other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
