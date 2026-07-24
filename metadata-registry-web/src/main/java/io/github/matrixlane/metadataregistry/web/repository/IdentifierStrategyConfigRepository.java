package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierStrategyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface IdentifierStrategyConfigRepository extends JpaRepository<IdentifierStrategyConfig, Long> {

    @Query("SELECT c FROM IdentifierStrategyConfig c WHERE c.status = 'PUBLISHED'")
    List<IdentifierStrategyConfig> findAllActiveConfigs();
}
