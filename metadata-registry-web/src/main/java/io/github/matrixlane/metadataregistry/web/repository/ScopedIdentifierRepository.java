package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
            WHERE si.namespace.id = :namespaceId
            AND si.identifier = :identifier
            ORDER BY si.createdAt DESC
            """)
    List<ScopedIdentifier> findAllByNamespaceIdAndIdentifier(@Param("namespaceId") UUID namespaceId,
                                                             @Param("identifier") String identifier);

    @Query("""
            SELECT si FROM ScopedIdentifier si
            WHERE si.identifier = :identifier
            ORDER BY si.createdAt DESC
            """)
    List<ScopedIdentifier> findByIdentifier(@Param("identifier") String identifier);

    @Query("""
            SELECT si FROM ScopedIdentifier si
            WHERE si.identifiedItem.id = :identifiedItemId
            ORDER BY si.createdAt DESC
            """)
    List<ScopedIdentifier> findByIdentifiedItemId(@Param("identifiedItemId") UUID identifiedItemId);

    @Query("""
            SELECT si FROM ScopedIdentifier si
            WHERE si.identifiedItem.item.id = :itemId
            """)
    List<ScopedIdentifier> findByItemId(@Param("itemId") UUID itemId);
}
