package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.MeasureClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface MeasureClassRepository
        extends JpaRepository<MeasureClass, UUID>, JpaSpecificationExecutor<MeasureClass> {
}
