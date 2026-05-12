package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ISO/IEC 11179-3:2023, 6.3.8 — Registration_Authority_Identifier
 *
 * <p>An identifier assigned by a registration authority.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.3.8")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class RegistrationAuthorityIdentifier {

    private String internationalCodeDesignator;

    private String organizationIdentifier;

    private String organizationPartIdentifier;

    private String opiSource;
}
