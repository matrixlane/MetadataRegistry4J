package io.github.matrixlane.metadataregistry.web.repository.fhir;

import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirElement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FhirElementRepository extends JpaRepository<FhirElement, UUID> {
    List<FhirElement> findByResourceIdOrderByElementPath(UUID resourceId);
    List<FhirElement> findByResourceIdAndParentIdIsNullOrderByElementPath(UUID resourceId);
    List<FhirElement> findByParentIdOrderByElementPath(UUID parentId);
    List<FhirElement> findByResourceIdAndPathStartingWithOrderByElementPath(UUID resourceId, String pathPrefix);
    long countByResourceId(UUID resourceId);
}
