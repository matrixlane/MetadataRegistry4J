package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface UnitOfMeasureRepository
        extends JpaRepository<UnitOfMeasure, UUID>, JpaSpecificationExecutor<UnitOfMeasure> {
}
