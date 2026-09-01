package io.github.matrixlane.metadataregistry.dataspecification;

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
public class DescribedConceptualDomain extends ConceptualDomain {

    @OneToMany(mappedBy = "conceptualDomain")
    private List<DescribedValueDomain> describedValueDomains = new ArrayList<>();
}
