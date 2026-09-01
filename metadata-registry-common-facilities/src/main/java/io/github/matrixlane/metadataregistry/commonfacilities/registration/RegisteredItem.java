package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public abstract class RegisteredItem extends IdentifiedItem {

    @ManyToOne
    @JoinColumn(name = "constraint_set_id")
    private ConstraintSet constraintSet;

    @ManyToMany
    @JoinTable(
            name = "registered_item_reference_document",
            joinColumns = @JoinColumn(name = "registered_item_id"),
            inverseJoinColumns = @JoinColumn(name = "reference_document_id")
    )
    private List<ReferenceDocument> referenceDocuments = new ArrayList<>();

    @ManyToMany(mappedBy = "registeredItems")
    private List<SubmissionRecord> submissionRecords = new ArrayList<>();

    public RegisteredItem(Item item) {
        super(item);
    }
}
