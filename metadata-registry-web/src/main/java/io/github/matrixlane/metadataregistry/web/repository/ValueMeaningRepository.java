package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.ValueMeaning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ValueMeaningRepository
        extends JpaRepository<ValueMeaning, UUID>, JpaSpecificationExecutor<ValueMeaning> {
}
