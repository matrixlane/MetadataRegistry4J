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

    /** ISO/IEC 11179-31:2023 表 5(Value_Domain):datatype 1,必备。 */
    @ManyToOne(optional = false)
    @JoinColumn(name = "data_type_id", nullable = false)
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
