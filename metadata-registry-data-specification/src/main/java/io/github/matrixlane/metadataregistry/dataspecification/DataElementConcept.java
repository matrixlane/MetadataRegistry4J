package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Concept;
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
public class DataElementConcept extends Concept {

    @ManyToOne
    @JoinColumn(name = "object_class_id")
    private ObjectClass objectClass;

    @ManyToOne
    @JoinColumn(name = "property_id")
    private Property property;

    @ManyToMany(mappedBy = "dataElementConcepts")
    private List<ConceptualDomain> conceptualDomains = new ArrayList<>();
}
