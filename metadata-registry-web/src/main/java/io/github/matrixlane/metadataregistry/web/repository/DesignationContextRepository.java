package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DesignationContextRepository extends JpaRepository<DesignationContext, UUID> {

    List<DesignationContext> findByContext_Id(UUID contextId);

    Page<DesignationContext> findByContext_Id(UUID contextId, Pageable pageable);

    @Query("""
            SELECT dc FROM DesignationContext dc
            LEFT JOIN FETCH dc.designation d
            LEFT JOIN FETCH d.item
            LEFT JOIN FETCH d.language
            WHERE dc.context.id = :contextId
            """)
    List<DesignationContext> findByContextIdWithDetails(@Param("contextId") UUID contextId);
}
