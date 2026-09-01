package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.ext.identification.NamespaceIdentifierSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NamespaceIdentifierSettingsRepository extends JpaRepository<NamespaceIdentifierSettings, UUID> {

    Optional<NamespaceIdentifierSettings> findByNamespaceId(UUID namespaceId);

    List<NamespaceIdentifierSettings> findByNamespaceIdIn(Collection<UUID> namespaceIds);
}
