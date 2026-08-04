package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierStrategyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface IdentifierStrategyConfigRepository extends JpaRepository<IdentifierStrategyConfig, Long> {

    @Query("SELECT c FROM IdentifierStrategyConfig c WHERE c.status = 'PUBLISHED'")
    List<IdentifierStrategyConfig> findAllActiveConfigs();

    @Query("SELECT c FROM IdentifierStrategyConfig c WHERE c.namespace.id = :namespaceId ORDER BY c.version DESC")
    List<IdentifierStrategyConfig> findByNamespaceIdOrderByVersionDesc(UUID namespaceId);
}
