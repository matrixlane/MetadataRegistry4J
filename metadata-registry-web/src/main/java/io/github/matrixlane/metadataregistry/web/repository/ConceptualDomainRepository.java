package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ConceptualDomainRepository
        extends JpaRepository<ConceptualDomain, UUID>, JpaSpecificationExecutor<ConceptualDomain> {

    /** 引用计数:被 conceptual_domain.dimensionality_id 引用的维度删除保护。 */
    long countByDimensionality_Id(UUID dimensionalityId);
}
