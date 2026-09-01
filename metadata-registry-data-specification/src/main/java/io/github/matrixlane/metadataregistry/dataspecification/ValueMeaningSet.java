package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Datetime_Period;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.Embedded;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class ValueMeaningSet extends Item {

    @Embedded
    private Datetime_Period validPeriod;

    @ManyToOne
    @JoinColumn(name = "value_meaning_id")
    private ValueMeaning valueMeaning;

    @ManyToOne
    @JoinColumn(name = "local_enumerated_conceptual_domain_id")
    private LocalEnumeratedConceptualDomain localEnumeratedConceptualDomain;
}
