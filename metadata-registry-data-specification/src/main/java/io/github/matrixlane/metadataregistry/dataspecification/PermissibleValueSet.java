package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.DataTimePeriod;
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
public class PermissibleValueSet extends Item {

    @Embedded
    private DataTimePeriod validPeriod;

    @ManyToOne
    @JoinColumn(name = "permissible_value_id")
    private PermissibleValue permissibleValue;

    @ManyToOne
    @JoinColumn(name = "local_enumerated_value_domain_id")
    private LocalEnumeratedValueDomain localEnumeratedValueDomain;
}
