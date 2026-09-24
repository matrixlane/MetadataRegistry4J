package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LanguageIdentificationExtensionRepository extends JpaRepository<LanguageIdentificationExtension, UUID> {

    Optional<LanguageIdentificationExtension> findByLanguageId(UUID languageId);

    List<LanguageIdentificationExtension> findByLanguageIdIn(Collection<UUID> languageIds);
}
