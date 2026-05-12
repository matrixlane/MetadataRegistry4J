package io.github.matrixlane.metadataregistry.dataspecification;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class DescribedValueDomain extends ValueDomain {

    @ManyToOne
    @JoinColumn(name = "described_conceptual_domain_id")
    private DescribedConceptualDomain describedConceptualDomain;
}
