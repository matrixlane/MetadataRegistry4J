package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.ClassificationNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClassificationNodeRepository extends JpaRepository<ClassificationNode, UUID> {

    List<ClassificationNode> findByClassificationSchemeIdOrderBySortOrderAsc(UUID schemeId);

    List<ClassificationNode> findByParentIdOrderBySortOrderAsc(UUID parentId);

    List<ClassificationNode> findByParentIdIsNullAndClassificationSchemeIdOrderBySortOrderAsc(UUID schemeId);

    boolean existsByClassificationSchemeIdAndNodeCode(UUID schemeId, String nodeCode);

    @Query("SELECT CASE WHEN COUNT(n) > 0 THEN true ELSE false END FROM ClassificationNode n WHERE n.parentId = :parentId")
    boolean hasChildren(@Param("parentId") UUID parentId);

    long countByClassificationSchemeId(UUID schemeId);
}
