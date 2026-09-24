package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.Dimensionality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface DimensionalityRepository
        extends JpaRepository<Dimensionality, UUID>, JpaSpecificationExecutor<Dimensionality> {

    List<Dimensionality> findByMeasureClasses_Id(UUID id);
}
