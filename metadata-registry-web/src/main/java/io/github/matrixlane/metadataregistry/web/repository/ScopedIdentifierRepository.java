package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScopedIdentifierRepository extends JpaRepository<ScopedIdentifier, UUID> {

    Optional<ScopedIdentifier> findByNamespaceIdAndIdentifierAndVersion(UUID namespaceId,
                                                                       String identifier,
                                                                       String version);
}
