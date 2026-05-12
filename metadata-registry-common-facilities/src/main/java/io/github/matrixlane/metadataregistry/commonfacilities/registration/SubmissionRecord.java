package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class SubmissionRecord extends Entity {

    @ManyToOne
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @ManyToMany
    @JoinTable(
            name = "submission_record_registered_item",
            joinColumns = @JoinColumn(name = "submission_record_id"),
            inverseJoinColumns = @JoinColumn(name = "registered_item_id")
    )
    private List<RegisteredItem> registeredItems = new ArrayList<>();
}
