package io.github.matrixlane.metadataregistry.web.repository.fhir;

import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirElement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface FhirElementRepository extends JpaRepository<FhirElement, UUID> {
    List<FhirElement> findByResourceIdOrderByElementPath(UUID resourceId);
    List<FhirElement> findByResourceIdAndParentIdIsNullOrderByElementPath(UUID resourceId);
    List<FhirElement> findByParentIdOrderByElementPath(UUID parentId);
    List<FhirElement> findByResourceIdAndPathStartingWithOrderByElementPath(UUID resourceId, String pathPrefix);
    long countByResourceId(UUID resourceId);

    /**
     * Finds all elements of a resource that have a local mapping (administered_item_id is not null).
     */
    List<FhirElement> findByResourceIdAndAdministeredItemIdIsNotNull(UUID resourceId);

    /**
     * Finds all elements of a resource that have no local mapping.
     */
    List<FhirElement> findByResourceIdAndAdministeredItemIdIsNull(UUID resourceId);

    /**
     * Counts elements by resource and whether they have a mapping.
     */
    long countByResourceIdAndAdministeredItemIdIsNotNull(UUID resourceId);
    long countByResourceIdAndAdministeredItemIdIsNull(UUID resourceId);

    /**
     * Finds all distinct administered_item_id values mapped to elements of the given resource.
     */
    @Query("SELECT DISTINCT e.administeredItemId FROM FhirElement e WHERE e.resource.id = :resourceId AND e.administeredItemId IS NOT NULL")
    List<UUID> findDistinctAdministeredItemIdsByResourceId(@Param("resourceId") UUID resourceId);

    /**
     * Finds all distinct administered_item_id values across all FHIR elements (any resource).
     */
    @Query("SELECT DISTINCT e.administeredItemId FROM FhirElement e WHERE e.administeredItemId IS NOT NULL")
    List<UUID> findAllMappedAdministeredItemIds();
}
