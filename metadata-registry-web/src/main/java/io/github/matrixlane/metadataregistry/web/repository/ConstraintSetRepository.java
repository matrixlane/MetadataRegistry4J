package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.ConstraintSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ConstraintSetRepository
        extends JpaRepository<ConstraintSet, UUID>, JpaSpecificationExecutor<ConstraintSet> {
}
