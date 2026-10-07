package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.ext.registration.RegistrarExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RegistrarExtensionRepository extends JpaRepository<RegistrarExtension, UUID> {
}
