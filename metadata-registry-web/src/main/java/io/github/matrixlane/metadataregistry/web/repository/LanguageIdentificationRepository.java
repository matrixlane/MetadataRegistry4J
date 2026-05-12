package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface LanguageIdentificationRepository
        extends JpaRepository<LanguageIdentification, UUID>,
                JpaSpecificationExecutor<LanguageIdentification> {

    Optional<LanguageIdentification> findByLanguageIdentifier(String languageIdentifier);

    boolean existsByLanguageIdentifier(String languageIdentifier);
}
