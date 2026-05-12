package io.github.matrixlane.metadataregistry.web.dto.approval;

import io.github.matrixlane.metadataregistry.web.entity.ApprovalResult;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApprovalRecordResponse(
        UUID id,
        UUID administeredItemId,
        String submitter,
        LocalDateTime submittedAt,
        String approver,
        LocalDateTime approvedAt,
        String opinion,
        ApprovalResult result) {
}
