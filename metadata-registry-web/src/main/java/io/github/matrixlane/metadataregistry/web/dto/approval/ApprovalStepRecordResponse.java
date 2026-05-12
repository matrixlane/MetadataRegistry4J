package io.github.matrixlane.metadataregistry.web.dto.approval;

import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepAssigneeRule;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApprovalStepRecordResponse(
        UUID id,
        UUID processInstanceId,
        Integer stepNumber,
        String stepName,
        ApprovalStepAssigneeRule assigneeRule,
        String assigneeValue,
        String approver,
        LocalDateTime assignedAt,
        LocalDateTime approvedAt,
        String opinion,
        ApprovalStepStatus status,
        LocalDateTime timeoutAt) {
}
