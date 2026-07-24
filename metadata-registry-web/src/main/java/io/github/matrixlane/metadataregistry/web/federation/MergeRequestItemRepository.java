package io.github.matrixlane.metadataregistry.web.federation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MergeRequestItemRepository extends JpaRepository<MergeRequestItem, UUID> {

    @Query("SELECT i FROM MergeRequestItem i WHERE i.mergeRequest.id = :mrId")
    List<MergeRequestItem> findByMergeRequestId(@Param("mrId") UUID mrId);
}
