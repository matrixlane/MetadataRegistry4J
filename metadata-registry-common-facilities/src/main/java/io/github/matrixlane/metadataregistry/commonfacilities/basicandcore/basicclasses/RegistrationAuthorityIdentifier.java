package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ISO/IEC 11179-3:2023, 6.3.9 — Registration_Authority_Identifier
 *
 * <p>An identifier assigned by a registration authority.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.3.9")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class RegistrationAuthorityIdentifier {

    /** 表 8:international_code_designator 1,必备。 */
    @jakarta.persistence.Column(nullable = false)
    private String internationalCodeDesignator;

    /** 表 8:organization_identifier 1,必备。 */
    @jakarta.persistence.Column(nullable = false)
    private String organizationIdentifier;

    private String organizationPartIdentifier;

    private String opiSource;
}
