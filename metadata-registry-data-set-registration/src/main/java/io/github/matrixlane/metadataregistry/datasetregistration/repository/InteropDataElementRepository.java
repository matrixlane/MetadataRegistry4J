package io.github.matrixlane.metadataregistry.datasetregistration.repository;

import io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataElement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing {@link InteropDataElement} entities.
 */
@Repository
public interface InteropDataElementRepository extends JpaRepository<InteropDataElement, UUID> {

    /**
     * Find all data elements belonging to a specific dataset.
     */
    List<InteropDataElement> findByDataSetId(UUID dataSetId);

    /**
     * Find a specific data element by dataset ID and data element identifier.
     */
    Optional<InteropDataElement> findByDataSetIdAndDeIdentifier(UUID dataSetId, String deIdentifier);

    /**
     * Count data elements in a dataset.
     */
    long countByDataSetId(UUID dataSetId);
}
