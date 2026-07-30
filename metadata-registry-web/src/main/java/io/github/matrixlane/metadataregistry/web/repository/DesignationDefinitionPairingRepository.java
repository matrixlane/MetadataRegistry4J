package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationDefinitionPairing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DesignationDefinitionPairingRepository extends JpaRepository<DesignationDefinitionPairing, UUID> {

    List<DesignationDefinitionPairing> findByDesignation_Item_Id(UUID itemId);

    void deleteByDesignation_Item_Id(UUID itemId);

    List<DesignationDefinitionPairing> findByContext_Id(UUID contextId);

    Page<DesignationDefinitionPairing> findByContext_Id(UUID contextId, Pageable pageable);

    @Query("""
            SELECT p FROM DesignationDefinitionPairing p
            LEFT JOIN FETCH p.designation d
            LEFT JOIN FETCH d.item
            LEFT JOIN FETCH p.definition def
            LEFT JOIN FETCH def.item
            WHERE p.context.id = :contextId
            """)
    List<DesignationDefinitionPairing> findByContextIdWithDetails(@Param("contextId") UUID contextId);
}
