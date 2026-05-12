package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.web.entity.ApprovalRecord;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, UUID> {

    List<ApprovalRecord> findByAdministeredItemIdOrderBySubmittedAtDesc(UUID administeredItemId);

    Optional<ApprovalRecord> findFirstByAdministeredItemIdAndResultOrderBySubmittedAtDesc(
            UUID administeredItemId, ApprovalResult result);

    boolean existsByAdministeredItemIdAndResult(UUID administeredItemId, ApprovalResult result);
}
