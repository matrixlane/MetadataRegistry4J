package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.ValueMeaningSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ValueMeaningSetRepository extends JpaRepository<ValueMeaningSet, UUID> {
    List<ValueMeaningSet> findByLocalEnumeratedConceptualDomainId(UUID localEnumeratedConceptualDomainId);
}
