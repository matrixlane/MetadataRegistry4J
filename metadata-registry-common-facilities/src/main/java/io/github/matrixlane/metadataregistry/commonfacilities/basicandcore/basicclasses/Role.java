package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
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

    /** 11179-3:2023 表 1:email_address 0..*,String (6.2.11)。多重性 0..* 由任务 1.4 处理。 */
    @Column(name = "email_address")
    private String emailAddress;

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
