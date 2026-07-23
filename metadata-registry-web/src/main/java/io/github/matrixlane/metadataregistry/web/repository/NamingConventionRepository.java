package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface NamingConventionRepository
        extends JpaRepository<NamingConvention, UUID>, JpaSpecificationExecutor<NamingConvention> {
}
