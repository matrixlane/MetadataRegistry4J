package io.github.matrixlane.metadataregistry.web.repository.fhir;

import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirValueSet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FhirValueSetRepository extends JpaRepository<FhirValueSet, UUID> {
    Optional<FhirValueSet> findByUrl(String url);
    boolean existsByUrl(String url);
}
