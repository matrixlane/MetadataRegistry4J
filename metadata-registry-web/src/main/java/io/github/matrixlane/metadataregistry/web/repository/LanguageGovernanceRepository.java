package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageGovernance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LanguageGovernanceRepository extends JpaRepository<LanguageGovernance, UUID> {

    Optional<LanguageGovernance> findByLanguageId(UUID languageId);

    List<LanguageGovernance> findByLanguageIdIn(Collection<UUID> languageIds);

    List<LanguageGovernance> findByEnabledFalse();
}
