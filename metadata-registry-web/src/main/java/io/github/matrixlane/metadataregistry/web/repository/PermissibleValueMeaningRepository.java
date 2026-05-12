package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValueMeaning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface PermissibleValueMeaningRepository
        extends JpaRepository<PermissibleValueMeaning, UUID>, JpaSpecificationExecutor<PermissibleValueMeaning> {
    List<PermissibleValueMeaning> findByPermissibleValues_Id(UUID permissibleValueId);
}
