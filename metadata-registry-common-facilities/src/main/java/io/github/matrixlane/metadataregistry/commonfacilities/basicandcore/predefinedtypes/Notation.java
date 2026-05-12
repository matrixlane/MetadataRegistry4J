package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ISO/IEC 11179-3:2023, 6.2.7 — Notation
 *
 * <p>A set of signs and the rules for their use in representing data.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2.7")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notation implements IValueType<String> {

    private String value;

    public static Notation of(String value) {
        return new Notation(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
