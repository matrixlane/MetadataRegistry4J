package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.EmailAddress;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PostalAddress;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ISO/IEC 11179-3:2023, 6.3.4 — Role
 *
 * <p>A set of behaviours, rights, and obligations associated with an
 * {@link Individual} or {@link Organization} in relation to registry content.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.3.4")
@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class Role extends Entity {

    private String roleName;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "mail_address"))
    })
    private PostalAddress mailAddress = new PostalAddress();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "email_address"))
    })
    private EmailAddress emailAddress = new EmailAddress();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "phone_number"))
    })
    private PhoneNumber phoneNumber = new PhoneNumber();

    @Override
    public String toString() {
        return roleName;
    }
}
