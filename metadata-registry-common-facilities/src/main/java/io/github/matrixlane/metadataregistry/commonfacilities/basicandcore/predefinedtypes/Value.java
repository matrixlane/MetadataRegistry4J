package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * ISO/IEC 11179-3:2023, 6.2.12 — Value
 * <p>
 * A data value representation that may carry any primitive value.
 * This embeddable holds the value as a String together with an optional
 * datatype URI hint; it does not restrict the data types available in
 * the registry content.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2.12")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Value implements IValueType<String> {

    private String value;

    /**
     * Optional URI identifying the concrete datatype of this value.
     */
    private String datatypeUri;

    public static Value of(String value) {
        return new Value(value, null);
    }

    public static Value of(String value, String datatypeUri) {
        return new Value(value, datatypeUri);
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Value other)) return false;
        return Objects.equals(value, other.value) && Objects.equals(datatypeUri, other.datatypeUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, datatypeUri);
    }
}
