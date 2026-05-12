package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface RegistrationAuthorityRepository
        extends JpaRepository<RegistrationAuthority, UUID>,
                JpaSpecificationExecutor<RegistrationAuthority> {
}
