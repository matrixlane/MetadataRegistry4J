package io.github.matrixlane.metadataregistry.itemmapping.repository;

import io.github.matrixlane.metadataregistry.itemmapping.entity.InteropMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.InteropMappingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link InteropMapping} entities.
 *
 * <p>Provides data access operations for interoperability-to-local
 * mapping results, including querying by interop element, local item,
 * strategy, status, dataset, and confidence score range.
 */
@Repository
public interface InteropMappingRepository extends JpaRepository<InteropMapping, UUID> {

    /**
     * Finds all mappings for a given interoperability data element.
     *
     * @param interopDataElementId the interop data element UUID
     * @return list of mappings for that element
     */
    List<InteropMapping> findByInteropDataElementId(UUID interopDataElementId);

    /**
     * Finds all mappings that point to a given local item.
     *
     * @param localItemId the local item UUID
     * @return list of mappings for that item
     */
    List<InteropMapping> findByLocalItemId(UUID localItemId);

    /**
     * Finds mappings by status.
     *
     * @param status the mapping status
     * @return list of mappings in that status
     */
    List<InteropMapping> findByStatus(InteropMappingStatus status);

    /**
     * Finds mappings by strategy type.
     *
     * @param strategy the strategy name (e.g. "EXACT_IDENTIFIER")
     * @return list of mappings produced by that strategy
     */
    List<InteropMapping> findByStrategy(String strategy);

    /**
     * Finds the single best mapping for a given interop data element,
     * ordered by confidence score descending.
     *
     * @param interopDataElementId the interop data element UUID
     * @return the highest-confidence mapping, if any
     */
    Optional<InteropMapping> findTopByInteropDataElementIdOrderByConfidenceScoreDesc(UUID interopDataElementId);

    /**
     * Checks whether any mapping already exists between the given interop
     * element and local item.
     *
     * @param interopDataElementId the interop data element UUID
     * @param localItemId          the local item UUID
     * @return true if a mapping exists
     */
    boolean existsByInteropDataElementIdAndLocalItemId(UUID interopDataElementId, UUID localItemId);

    // ==================== Dataset & Status Queries ====================

    /**
     * Finds all mappings for a given dataset.
     *
     * @param datasetId the dataset UUID
     * @return list of mappings in that dataset
     */
    List<InteropMapping> findByDatasetId(UUID datasetId);

    /**
     * Finds mappings by dataset and status.
     *
     * @param datasetId the dataset UUID
     * @param status    the mapping status
     * @return list of mappings matching criteria
     */
    List<InteropMapping> findByDatasetIdAndStatus(UUID datasetId, InteropMappingStatus status);

    /**
     * Counts mappings by dataset and status.
     *
     * @param datasetId the dataset UUID
     * @param status    the mapping status
     * @return count of matching mappings
     */
    long countByDatasetIdAndStatus(UUID datasetId, InteropMappingStatus status);

    /**
     * Counts all mappings by dataset.
     *
     * @param datasetId the dataset UUID
     * @return total count of mappings in the dataset
     */
    long countByDatasetId(UUID datasetId);

    /**
     * Finds mappings by dataset and status with pagination.
     *
     * @param datasetId the dataset UUID
     * @param status    the mapping status
     * @param pageable  pagination parameters
     * @return page of mappings
     */
    Page<InteropMapping> findByDatasetIdAndStatus(UUID datasetId, InteropMappingStatus status, Pageable pageable);

    /**
     * Finds mappings by dataset with pagination.
     *
     * @param datasetId the dataset UUID
     * @param pageable  pagination parameters
     * @return page of mappings
     */
    Page<InteropMapping> findByDatasetId(UUID datasetId, Pageable pageable);

    // ==================== Composite Filter Queries ====================

    /**
     * Finds mappings by dataset with optional status filter and confidence range,
     * ordered by confidence score descending.
     *
     * @param datasetId        the dataset UUID
     * @param status           optional status filter (null = all statuses)
     * @param minConfidence    optional minimum confidence score (inclusive)
     * @param maxConfidence    optional maximum confidence score (inclusive)
     * @param pageable         pagination parameters
     * @return page of filtered mappings
     */
    @Query("""
            SELECT m FROM InteropMapping m
            WHERE m.datasetId = :datasetId
              AND (:status IS NULL OR m.status = :status)
              AND (:minConfidence IS NULL OR m.confidenceScore >= :minConfidence)
              AND (:maxConfidence IS NULL OR m.confidenceScore <= :maxConfidence)
            ORDER BY m.confidenceScore DESC
            """)
    Page<InteropMapping> findByDatasetAndFilters(
            @Param("datasetId") UUID datasetId,
            @Param("status") InteropMappingStatus status,
            @Param("minConfidence") BigDecimal minConfidence,
            @Param("maxConfidence") BigDecimal maxConfidence,
            Pageable pageable);

    /**
     * Finds all PENDING mappings for a given dataset.
     *
     * @param datasetId the dataset UUID
     * @return list of pending mappings
     */
    List<InteropMapping> findByDatasetIdAndStatusOrderByConfidenceScoreDesc(UUID datasetId, InteropMappingStatus status);

    // ==================== Batch Operation Queries ====================

    /**
     * Finds all mappings affected by a specific batch operation.
     *
     * @param batchOperationId the batch operation UUID
     * @return list of mappings modified by the batch operation
     */
    List<InteropMapping> findByBatchOperationId(UUID batchOperationId);

    // ==================== Update Queries ====================

    /**
     * Updates the status of all mappings for a given interop data element.
     *
     * @param interopDataElementId the interop data element UUID
     * @param newStatus            the new status to set
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE InteropMapping m SET m.status = :newStatus WHERE m.interopDataElementId = :interopDataElementId")
    int updateStatusByInteropDataElementId(@Param("interopDataElementId") UUID interopDataElementId,
                                            @Param("newStatus") InteropMappingStatus newStatus);

    /**
     * Updates the batch operation ID for mappings by dataset and status.
     *
     * @param datasetId        the dataset UUID
     * @param status           the status to match
     * @param batchOperationId the batch operation UUID to set
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE InteropMapping m SET m.batchOperationId = :batchOperationId WHERE m.datasetId = :datasetId AND m.status = :status")
    int updateBatchOperationIdByDatasetAndStatus(
            @Param("datasetId") UUID datasetId,
            @Param("status") InteropMappingStatus status,
            @Param("batchOperationId") UUID batchOperationId);
}
