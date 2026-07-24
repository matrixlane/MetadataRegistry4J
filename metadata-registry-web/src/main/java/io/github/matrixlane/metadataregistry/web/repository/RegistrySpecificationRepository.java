package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface RegistrySpecificationRepository
        extends JpaRepository<RegistrySpecification, UUID>, JpaSpecificationExecutor<RegistrySpecification> {
}
