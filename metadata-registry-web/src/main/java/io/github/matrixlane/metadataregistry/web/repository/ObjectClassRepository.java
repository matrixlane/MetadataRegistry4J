package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ObjectClassRepository
        extends JpaRepository<ObjectClass, UUID>, JpaSpecificationExecutor<ObjectClass> {
}
