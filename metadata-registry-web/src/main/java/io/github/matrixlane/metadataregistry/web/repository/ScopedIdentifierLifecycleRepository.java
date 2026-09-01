package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.ext.identification.ScopedIdentifierLifecycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScopedIdentifierLifecycleRepository extends JpaRepository<ScopedIdentifierLifecycle, UUID> {

    Optional<ScopedIdentifierLifecycle> findByScopedIdentifierId(UUID scopedIdentifierId);

    List<ScopedIdentifierLifecycle> findByScopedIdentifierIdIn(Collection<UUID> scopedIdentifierIds);
}
