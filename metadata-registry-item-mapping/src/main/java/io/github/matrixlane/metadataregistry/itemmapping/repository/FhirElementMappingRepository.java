package io.github.matrixlane.metadataregistry.itemmapping.repository;

import io.github.matrixlane.metadataregistry.itemmapping.entity.FhirElementMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link FhirElementMapping} entities.
 *
 * <p>Provides data access for local AdministeredItem ↔ FHIR Element mappings
 * including conflict detection queries.
 */
@Repository
public interface FhirElementMappingRepository extends JpaRepository<FhirElementMapping, UUID> {

    /**
     * Finds all mappings for a given local item.
     */
    List<FhirElementMapping> findByLocalItemId(UUID localItemId);

    /**
     * Finds all mappings for a given FHIR path.
     */
    List<FhirElementMapping> findByFhirPath(String fhirPath);

    /**
     * Finds all mappings for a given FHIR resource type.
     */
    List<FhirElementMapping> findByFhirResourceType(String fhirResourceType);

    /**
     * Finds all mappings for a local item within a specific resource type.
     */
    List<FhirElementMapping> findByLocalItemIdAndFhirResourceType(UUID localItemId,
                                                                   String fhirResourceType);

    /**
     * Finds a specific mapping by local item and FHIR path.
     */
    Optional<FhirElementMapping> findByLocalItemIdAndFhirPath(UUID localItemId,
                                                               String fhirPath);

    /**
     * Checks whether a mapping exists for the given local item and FHIR path.
     */
    boolean existsByLocalItemIdAndFhirPath(UUID localItemId, String fhirPath);

    /**
     * Counts how many FHIR Elements a local item is mapped to.
     */
    long countByLocalItemId(UUID localItemId);

    /**
     * Finds all mappings of a specific cardinality type.
     */
    List<FhirElementMapping> findByMappingType(FhirMappingType mappingType);

    /**
     * Finds all local item IDs that have more than one FHIR mapping.
     * Used for bulk conflict detection.
     *
     * @return list of local item UUIDs with multiple mappings
     */
    @Query("SELECT m.localItemId FROM FhirElementMapping m " +
           "GROUP BY m.localItemId HAVING COUNT(m) > 1")
    List<UUID> findLocalItemIdsWithMultipleMappings();

    /**
     * Finds all mappings grouped by local item ID for conflict analysis.
     * Returns items that map to more than one distinct FHIR path.
     */
    @Query("SELECT m.localItemId FROM FhirElementMapping m " +
           "GROUP BY m.localItemId HAVING COUNT(DISTINCT m.fhirPath) > 1")
    List<UUID> findConflictingLocalItemIds();

    /**
     * Finds all mappings for a set of local item IDs.
     */
    @Query("SELECT m FROM FhirElementMapping m WHERE m.localItemId IN :itemIds")
    List<FhirElementMapping> findByLocalItemIds(@Param("itemIds") List<UUID> itemIds);
}
