package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PostalAddress;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.JoinColumn;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

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

    /** 11179-3:2023 表 3(Role):email_address 0..* String (6.2.11)。 */
    @ElementCollection
    @CollectionTable(name = "role_email", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "value")
    private List<String> emailAddress = new ArrayList<>();

    /** 表 3:phone_number 0..* Phone_Number (6.2.8)。 */
    @ElementCollection
    @CollectionTable(name = "role_phone", joinColumns = @JoinColumn(name = "role_id"))
    private List<PhoneNumber> phoneNumber = new ArrayList<>();

    @Override
    public String toString() {
        return roleName;
    }
}
