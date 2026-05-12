package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.EmailAddress;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PostalAddress;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * ISO/IEC 11179-3:2023, 6.3.3 — Organization
 *
 * <p>A unique framework of authority within which individuals act.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.3.3")
@jakarta.persistence.Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
public class Organization extends Entity {

    @ElementCollection
    @CollectionTable(name = "organization_name", joinColumns = @JoinColumn(name = "organization_id"))
    private List<Sign> name = new ArrayList<>();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "mail_address"))
    })
    private PostalAddress mailAddress = new PostalAddress();

    @ElementCollection
    @CollectionTable(name = "organization_email", joinColumns = @JoinColumn(name = "organization_id"))
    private List<EmailAddress> emailAddress = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "organization_phone", joinColumns = @JoinColumn(name = "organization_id"))
    private List<PhoneNumber> phoneNumber = new ArrayList<>();

    private String webAddress;

    @OneToMany(mappedBy = "organization")
    private List<Contact> contact = new ArrayList<>();

    @Override
    public String toString() {
        return name.isEmpty() ? null : name.get(0).toString();
    }
}
