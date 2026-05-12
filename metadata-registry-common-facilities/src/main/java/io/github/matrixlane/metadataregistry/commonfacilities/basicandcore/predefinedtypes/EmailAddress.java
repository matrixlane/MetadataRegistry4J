package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ISO/IEC 11179-3:2023, 6.2.4 — Email_Address (project extension)
 *
 * <p>DEVIATION: Email_Address is not explicitly listed in the 12 predefined types
 * of Clause 6.2, but is used throughout the model for contact information.
 */
@StandardReference(value = "ISO/IEC 11179-3:2023, 6.2",
        deviation = "Not among the 12 explicit predefined types in Clause 6.2, but used as a utility type.")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailAddress implements IValueType<String> {

    private String value;

    public static EmailAddress of(String value) {
        return new EmailAddress(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
