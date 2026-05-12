package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValueSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PermissibleValueSetRepository extends JpaRepository<PermissibleValueSet, UUID> {

    List<PermissibleValueSet> findByLocalEnumeratedValueDomainId(UUID localEnumeratedValueDomainId);
}
