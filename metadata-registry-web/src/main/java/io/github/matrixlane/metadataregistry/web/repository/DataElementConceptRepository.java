package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface DataElementConceptRepository
        extends JpaRepository<DataElementConcept, UUID>, JpaSpecificationExecutor<DataElementConcept> {
}
