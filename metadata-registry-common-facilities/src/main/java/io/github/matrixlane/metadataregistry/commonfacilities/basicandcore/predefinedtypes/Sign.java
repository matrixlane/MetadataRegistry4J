package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * ISO/IEC 11179-3:2023, 6.2.10 — Sign
 *
 * <p>A perceptible representation, such as a symbol, graphic, or sound fragment,
 * used to convey meaning.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2.10")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Sign implements IValueType<String> {

    private String value;

    public static Sign of(String value) {
        return new Sign(value);
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Sign other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
