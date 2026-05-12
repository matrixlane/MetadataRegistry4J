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
public class Dimensionality extends Concept {

    private boolean coordinateIndicator;

    @ManyToMany
    @JoinTable(
            name = "dimensionality_measure_class",
            joinColumns = @JoinColumn(name = "dimensionality_id"),
            inverseJoinColumns = @JoinColumn(name = "measure_class_id")
    )
    private List<MeasureClass> measureClasses = new ArrayList<>();
}
