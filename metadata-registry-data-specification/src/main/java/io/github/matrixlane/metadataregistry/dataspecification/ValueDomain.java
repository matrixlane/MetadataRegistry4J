package io.github.matrixlane.metadataregistry.dataspecification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Datetime_Period;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import jakarta.persistence.Embedded;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
public abstract class ValueDomain extends Item {

    @ManyToOne
    @JoinColumn(name = "data_type_id")
    private DataType dataType;

    @Embedded
    private Datetime_Period validPeriod;

    private Integer maximumCharacterQuantity;

    private String format;

    @ManyToOne
    @JoinColumn(name = "unit_of_measure_id")
    private UnitOfMeasure unitOfMeasure;

    @OneToMany(mappedBy = "valueDomain")
    private List<DataElement> dataElements = new ArrayList<>();
}
