package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.ext.registration.RegistrationAuthorityExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RegistrationAuthorityExtensionRepository
        extends JpaRepository<RegistrationAuthorityExtension, UUID> {
}
