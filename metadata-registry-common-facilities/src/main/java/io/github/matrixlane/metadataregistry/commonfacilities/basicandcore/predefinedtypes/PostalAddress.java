package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * ISO/IEC 11179-3:2023, 6.2.9 — Postal_Address
 *
 * <p>Information that allows the identification of a specific mailing destination.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2.9")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostalAddress implements IValueType<String> {

    private String value;

    public static PostalAddress of(String value) {
        return new PostalAddress(value);
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostalAddress other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
