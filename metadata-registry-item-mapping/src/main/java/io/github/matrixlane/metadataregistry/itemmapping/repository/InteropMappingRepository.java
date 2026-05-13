package io.github.matrixlane.metadataregistry.itemmapping.repository;

import io.github.matrixlane.metadataregistry.itemmapping.entity.InteropMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.InteropMappingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link InteropMapping} entities.
 *
 * <p>Provides data access operations for interoperability-to-local
 * mapping results, including querying by interop element, local item,
 * strategy, and status.
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
}
