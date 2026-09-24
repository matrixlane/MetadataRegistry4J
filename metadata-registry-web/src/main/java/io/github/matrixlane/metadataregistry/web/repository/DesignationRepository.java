package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DesignationRepository extends JpaRepository<Designation, UUID> {

    List<Designation> findByItem_Id(UUID itemId);

    @Query("""
            SELECT d FROM Designation d
            JOIN d.namespaces ns
            WHERE ns.id = :namespaceId
            AND d.sign.value = :signValue
            AND (:excludeItemId IS NULL OR d.item.id != :excludeItemId)
            """)
    List<Designation> findByNamespaceIdAndSignValueAndItemIdNot(
            @Param("namespaceId") UUID namespaceId,
            @Param("signValue") String signValue,
            @Param("excludeItemId") UUID excludeItemId);

    @Query("""
            SELECT COUNT(d) > 0 FROM Designation d
            JOIN d.namespaces ns
            WHERE ns.id = :namespaceId
            AND d.sign.value = :signValue
            AND (:excludeItemId IS NULL OR d.item.id != :excludeItemId)
            """)
    boolean existsByNamespaceIdAndSignValueAndItemIdNot(
            @Param("namespaceId") UUID namespaceId,
            @Param("signValue") String signValue,
            @Param("excludeItemId") UUID excludeItemId);

    @Query("SELECT d FROM Designation d LEFT JOIN FETCH d.designationDefinitionPairings WHERE d.item.id = :itemId")
    List<Designation> findByItemIdWithPairings(@Param("itemId") UUID itemId);

    @Query("""
            SELECT d FROM Designation d
            JOIN d.designationDefinitionPairings pair
            WHERE d.item.id = :itemId
            AND d.language.id = :languageId
            AND pair.acceptability = io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability.PREFERRED
            """)
    List<Designation> findPreferredByItemIdAndLanguageId(
            @Param("itemId") UUID itemId,
            @Param("languageId") UUID languageId);

    /**
     * 瘦查询版首选指称（按 ItemNamingHelper.pickByLanguage 语言排序语义，避开 Item 联合子类整图加载——
     * SQL Server 选择列表 4096 列上限 / SQLite 64 表 join 上限）。
     */
    @Query("""
            SELECT d.sign.value FROM Designation d
            WHERE d.item.id = :itemId
            ORDER BY
                CASE WHEN d.language.languageIdentifier IS NULL THEN 9
                     WHEN d.language.languageIdentifier = 'zh-CN' THEN 0
                     WHEN d.language.languageIdentifier = 'en-US' THEN 1
                     ELSE 5 END,
                d.id
            """)
    List<String> findSignValuesByItemIdOrdered(@Param("itemId") UUID itemId);
}
