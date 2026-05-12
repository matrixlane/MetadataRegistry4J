package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ValueDomainRepository
        extends JpaRepository<ValueDomain, UUID>, JpaSpecificationExecutor<ValueDomain> {
}
