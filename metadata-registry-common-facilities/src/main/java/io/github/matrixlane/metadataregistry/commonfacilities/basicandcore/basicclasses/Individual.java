package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PostalAddress;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
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
 * ISO/IEC 11179-3:2023, 6.3.2 — Individual
 *
 * <p>A person who may be associated with a registry entry.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.3.2")
@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class Individual extends Entity {

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "name"))
    })
    private Sign name = new Sign();

    private String title;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "mail_address"))
    })
    private PostalAddress mailAddress = new PostalAddress();

    /** 11179-3:2023 表 2:email_address 0..*,String (6.2.11)。 */
    @ElementCollection
    @CollectionTable(name = "individual_email", joinColumns = @JoinColumn(name = "individual_id"))
    @Column(name = "value")
    private List<String> emailAddress = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "individual_phone", joinColumns = @JoinColumn(name = "individual_id"))
    private List<PhoneNumber> phoneNumber = new ArrayList<>();

    @Override
    public String toString() {
        return name == null ? null : name.toString();
    }
}
