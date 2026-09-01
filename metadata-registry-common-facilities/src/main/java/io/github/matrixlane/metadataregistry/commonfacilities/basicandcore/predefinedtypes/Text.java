package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * ISO/IEC 11179-3:2023, 6.2.12 — Text
 *
 * <p>Natural language text, distinct from a character string in that it carries
 * linguistic semantics.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2.12")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Text implements IValueType<String> {

    private String value;

    public static Text of(String value) {
        return new Text(value);
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Text other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
