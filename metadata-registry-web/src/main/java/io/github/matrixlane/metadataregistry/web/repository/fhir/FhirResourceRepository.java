package io.github.matrixlane.metadataregistry.web.repository.fhir;

import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

public interface FhirResourceRepository
        extends JpaRepository<FhirResource, UUID>, JpaSpecificationExecutor<FhirResource> {
    Optional<FhirResource> findByUrl(String url);
    Optional<FhirResource> findByName(String name);
    boolean existsByUrl(String url);
}
