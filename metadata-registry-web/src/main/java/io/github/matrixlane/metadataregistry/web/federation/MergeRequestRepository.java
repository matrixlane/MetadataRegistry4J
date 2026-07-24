package io.github.matrixlane.metadataregistry.web.federation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MergeRequestRepository extends JpaRepository<MergeRequest, UUID> {

    @Query("SELECT m FROM MergeRequest m WHERE m.sourceNode.id = :nodeId ORDER BY m.createdAt DESC")
    List<MergeRequest> findBySourceNodeId(@Param("nodeId") UUID nodeId);

    List<MergeRequest> findByStatusOrderByCreatedAtDesc(MergeRequestStatus status);
}
