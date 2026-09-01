package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Datetime_Period;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.Embedded;
import jakarta.persistence.JoinColumn;
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
public class PermissibleValueMeaning extends Item {

    @Embedded
    private Datetime_Period validPeriod;

    @ManyToOne
    @JoinColumn(name = "value_meaning_id")
    private ValueMeaning valueMeaning;

    @ManyToMany(mappedBy = "permissibleValueMeanings")
    private List<PermissibleValue> permissibleValues = new ArrayList<>();
}
