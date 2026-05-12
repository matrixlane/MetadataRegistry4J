package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * ISO/IEC 11179-3:2023, 6.2.5 — Integer
 * <p>
 * A whole number data type without fractional component.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2.5")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegerType implements IValueType<Long> {

    private Long value;

    public static IntegerType of(Long value) {
        return new IntegerType(value);
    }

    @Override
    public String toString() {
        return value == null ? null : value.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntegerType other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
