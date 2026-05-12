package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ISO/IEC 11179-3:2023, 6.2.8 — Phone_Number
 *
 * <p>A sequence of digits used to reach a specific telephone endpoint.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2.8")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PhoneNumber implements IValueType<String> {

    private String value;

    public static PhoneNumber of(String value) {
        return new PhoneNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
