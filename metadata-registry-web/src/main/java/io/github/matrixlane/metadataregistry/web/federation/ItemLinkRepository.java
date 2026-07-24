package io.github.matrixlane.metadataregistry.web.federation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ItemLinkRepository extends JpaRepository<ItemLink, UUID> {

    @Query("SELECT l FROM ItemLink l WHERE l.remoteNode.id = :nodeId AND l.remoteItemKey = :remoteItemKey")
    Optional<ItemLink> findByNodeAndRemoteKey(@Param("nodeId") UUID nodeId, @Param("remoteItemKey") String remoteItemKey);

    @Query("SELECT l FROM ItemLink l WHERE l.remoteNode.id = :nodeId AND l.localItemId = :localItemId")
    Optional<ItemLink> findByNodeAndLocalItem(@Param("nodeId") UUID nodeId, @Param("localItemId") UUID localItemId);
}
