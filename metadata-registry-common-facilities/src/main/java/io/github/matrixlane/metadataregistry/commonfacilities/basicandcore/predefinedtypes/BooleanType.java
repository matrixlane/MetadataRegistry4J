package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * ISO/IEC 11179-3:2023, 6.2.2 — Boolean
 * <p>
 * A logical data type that can have one of two values: true or false.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2.2")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BooleanType implements IValueType<Boolean> {

    private Boolean value;

    public static BooleanType of(Boolean value) {
        return new BooleanType(value);
    }

    @Override
    public String toString() {
        return value == null ? null : value.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BooleanType other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
