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
public class MeasureClass extends Concept {

    @ManyToMany(mappedBy = "measureClasses")
    private List<Dimensionality> dimensionalities = new ArrayList<>();

    @ManyToMany(mappedBy = "measureClasses")
    private List<UnitOfMeasure> unitOfMeasures = new ArrayList<>();
}
