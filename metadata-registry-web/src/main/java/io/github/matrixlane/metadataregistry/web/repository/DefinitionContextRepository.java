package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DefinitionContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DefinitionContextRepository extends JpaRepository<DefinitionContext, UUID> {

    List<DefinitionContext> findByContext_Id(UUID contextId);

    Page<DefinitionContext> findByContext_Id(UUID contextId, Pageable pageable);

    @Query("""
            SELECT dc FROM DefinitionContext dc
            LEFT JOIN FETCH dc.definition d
            LEFT JOIN FETCH d.item
            LEFT JOIN FETCH d.language
            WHERE dc.context.id = :contextId
            """)
    List<DefinitionContext> findByContextIdWithDetails(@Param("contextId") UUID contextId);
}
