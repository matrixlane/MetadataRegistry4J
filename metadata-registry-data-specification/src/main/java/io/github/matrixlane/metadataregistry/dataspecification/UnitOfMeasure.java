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
public class UnitOfMeasure extends Concept {

    @ManyToMany
    @JoinTable(
            name = "unit_of_measure_measure_class",
            joinColumns = @JoinColumn(name = "unit_of_measure_id"),
            inverseJoinColumns = @JoinColumn(name = "measure_class_id")
    )
    private List<MeasureClass> measureClasses = new ArrayList<>();
}
