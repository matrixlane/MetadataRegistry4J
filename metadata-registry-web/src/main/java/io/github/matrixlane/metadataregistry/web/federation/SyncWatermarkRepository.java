package io.github.matrixlane.metadataregistry.web.federation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SyncWatermarkRepository extends JpaRepository<SyncWatermark, UUID> {

    @Query("SELECT w FROM SyncWatermark w WHERE w.node.id = :nodeId")
    Optional<SyncWatermark> findByNodeId(@Param("nodeId") UUID nodeId);
}
