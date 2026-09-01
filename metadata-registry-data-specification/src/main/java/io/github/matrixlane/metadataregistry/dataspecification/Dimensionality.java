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

    /** 0..1 三态 Boolean(11179-31:2023 Table 22):NULL 表示 Not applicable。 */
    private Boolean coordinateIndicator;

    @ManyToMany
    @JoinTable(
            name = "dimensionality_measure_class",
            joinColumns = @JoinColumn(name = "dimensionality_id"),
            inverseJoinColumns = @JoinColumn(name = "measure_class_id")
    )
    private List<MeasureClass> measureClasses = new ArrayList<>();
}
