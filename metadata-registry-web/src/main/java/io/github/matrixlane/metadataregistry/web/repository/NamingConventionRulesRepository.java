package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.ext.designationanddefinition.NamingConventionRules;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NamingConventionRulesRepository extends JpaRepository<NamingConventionRules, UUID> {

    Optional<NamingConventionRules> findByNamingConventionId(UUID namingConventionId);
}
