package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface PermissibleValueRepository
        extends JpaRepository<PermissibleValue, UUID>, JpaSpecificationExecutor<PermissibleValue> {
}
