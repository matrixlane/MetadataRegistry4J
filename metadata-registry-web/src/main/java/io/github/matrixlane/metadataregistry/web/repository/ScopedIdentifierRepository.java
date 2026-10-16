package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScopedIdentifierRepository extends JpaRepository<ScopedIdentifier, UUID> {

    Optional<ScopedIdentifier> findByNamespaceIdAndIdentifierAndVersion(UUID namespaceId,
                                                                       String identifier,
                                                                       String version);

    Optional<ScopedIdentifier> findByNamespaceIdAndIdentifier(UUID namespaceId, String identifier);

    List<ScopedIdentifier> findByNamespaceId(UUID namespaceId);

    boolean existsByNamespaceIdAndIdentifier(UUID namespaceId, String identifier);

    @Query("""
            SELECT COUNT(si) > 0 FROM ScopedIdentifier si
            WHERE si.namespace.id = :namespaceId
            AND si.identifier = :identifier
            AND (:excludeIdentifiedItemId IS NULL OR si.identifiedItem.id != :excludeIdentifiedItemId)
            """)
    boolean existsByNamespaceIdAndIdentifierAndIdentifiedItemIdNot(
            @Param("namespaceId") UUID namespaceId,
            @Param("identifier") String identifier,
            @Param("excludeIdentifiedItemId") UUID excludeIdentifiedItemId);

    @Query("""
            SELECT si FROM ScopedIdentifier si
            LEFT JOIN ScopedIdentifierLifecycle lc ON lc.scopedIdentifierId = si.id
            WHERE si.namespace.id = :namespaceId
            AND si.identifier = :identifier
            ORDER BY lc.createdAt DESC
            """)
    List<ScopedIdentifier> findAllByNamespaceIdAndIdentifier(@Param("namespaceId") UUID namespaceId,
                                                             @Param("identifier") String identifier);

    @Query("""
            SELECT si FROM ScopedIdentifier si
            LEFT JOIN ScopedIdentifierLifecycle lc ON lc.scopedIdentifierId = si.id
            WHERE si.identifier = :identifier
            ORDER BY lc.createdAt DESC
            """)
    List<ScopedIdentifier> findByIdentifier(@Param("identifier") String identifier);

    @Query("""
            SELECT si FROM ScopedIdentifier si
            LEFT JOIN ScopedIdentifierLifecycle lc ON lc.scopedIdentifierId = si.id
            WHERE si.identifiedItem.id = :identifiedItemId
            ORDER BY lc.createdAt DESC
            """)
    List<ScopedIdentifier> findByIdentifiedItemId(@Param("identifiedItemId") UUID identifiedItemId);

    @Query("""
            SELECT si FROM ScopedIdentifier si
            WHERE si.identifiedItem.item.id = :itemId
            """)
    List<ScopedIdentifier> findByItemId(@Param("itemId") UUID itemId);

    /**
     * 一批条目的标识符**标量投影**，列序：
     * {@code [0]=itemId, [1]=scopedIdentifierId, [2]=identifier, [3]=version, [4]=namespaceId}。
     *
     * <p>{@link #findByItemId(UUID)} 返回 {@code ScopedIdentifier} 实体，而
     * {@code ScopedIdentifier.identifiedItem} 是默认 EAGER 的 {@code @ManyToOne}——
     * 加载它即触发 IdentifiedItem 的 JOINED 子类判别式展开，连带 AdministeredItem /
     * AttachedItem 及其上全部 EAGER 关联（注册机构、注册状态、主管记录→联系人→个人→
     * 组织→命名空间…），实测单次 44ms、731 逻辑读。列表页逐条调用即成 N+1
     * （2026-10-09）。本投影只取所需标量，不触碰任何继承展开。</p>
     */
    @Query("""
            SELECT si.identifiedItem.item.id, si.id, si.identifier, si.version, si.namespace.id
            FROM ScopedIdentifier si
            WHERE si.identifiedItem.item.id IN :itemIds
            """)
    List<Object[]> findScalarByItemIds(@Param("itemIds") Collection<UUID> itemIds);
}
