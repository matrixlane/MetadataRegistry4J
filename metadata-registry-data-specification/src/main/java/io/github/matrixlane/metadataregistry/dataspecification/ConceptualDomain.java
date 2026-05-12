package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Concept;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class ConceptualDomain extends Concept {

    @ManyToMany
    @JoinTable(
            name = "conceptual_domain_dec",
            joinColumns = @JoinColumn(name = "conceptual_domain_id"),
            inverseJoinColumns = @JoinColumn(name = "data_element_concept_id")
    )
    private List<DataElementConcept> dataElementConcepts = new ArrayList<>();
}
