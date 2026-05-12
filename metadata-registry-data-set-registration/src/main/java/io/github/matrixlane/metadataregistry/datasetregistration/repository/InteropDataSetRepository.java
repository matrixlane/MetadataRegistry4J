package io.github.matrixlane.metadataregistry.datasetregistration.repository;

import io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing {@link InteropDataSet} entities.
 */
@Repository
public interface InteropDataSetRepository extends JpaRepository<InteropDataSet, UUID> {

    /**
     * Find a dataset by its code.
     */
    Optional<InteropDataSet> findByCode(String code);

    /**
     * Check if a dataset with the given code already exists.
     */
    boolean existsByCode(String code);
}
