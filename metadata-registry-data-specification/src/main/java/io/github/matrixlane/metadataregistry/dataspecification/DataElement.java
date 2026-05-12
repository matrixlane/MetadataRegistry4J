package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class DataElement extends Item {

    @ManyToOne
    @JoinColumn(name = "data_element_concept_id")
    private DataElementConcept dataElementConcept;

    @ManyToOne
    @JoinColumn(name = "value_domain_id")
    private ValueDomain valueDomain;
}
