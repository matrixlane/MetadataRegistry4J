package io.github.matrixlane.metadataregistry.web.repository.fhir;

import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirCodeSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FhirCodeSystemRepository extends JpaRepository<FhirCodeSystem, UUID> {
    Optional<FhirCodeSystem> findByUrl(String url);
    boolean existsByUrl(String url);
}
