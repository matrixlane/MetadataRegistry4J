package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface RegistrarRepository
        extends JpaRepository<Registrar, UUID>, JpaSpecificationExecutor<Registrar> {

    List<Registrar> findByAccount_Id(UUID accountId);

    List<Registrar> findByAccount_Username(String username);
}
