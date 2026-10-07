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
    /** ISO/IEC 11179-3:2023 表 27(Submission_Record):organization 1,必备。 */
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne
    /** 表 27:contact 1,必备。 */
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @ManyToMany
    @JoinTable(
            name = "registered_item_submission_record",
            joinColumns = @JoinColumn(name = "submission_record_id"),
            inverseJoinColumns = @JoinColumn(name = "registered_item_id")
    )
    private List<RegisteredItem> registeredItems = new ArrayList<>();
}
