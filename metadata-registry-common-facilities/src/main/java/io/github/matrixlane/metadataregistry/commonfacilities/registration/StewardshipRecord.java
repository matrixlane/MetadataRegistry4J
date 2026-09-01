package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class StewardshipRecord extends Entity {

    @ManyToOne
    /** ISO/IEC 11179-3:2023 表 26(Stewardship_Record):organization 1,必备。 */
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne
    /** 表 26:contact 1,必备。 */
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @OneToMany(mappedBy = "stewardshipRecord")
    private List<AdministeredItem> administeredItems = new ArrayList<>();
}
