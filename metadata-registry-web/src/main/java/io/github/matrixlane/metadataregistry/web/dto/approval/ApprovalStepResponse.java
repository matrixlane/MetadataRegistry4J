package io.github.matrixlane.metadataregistry.web.dto.approval;

import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepAssigneeRule;
import io.github.matrixlane.metadataregistry.web.entity.RejectStrategy;

import java.util.UUID;

public record ApprovalStepResponse(
        UUID id,
        UUID templateId,
        Integer stepNumber,
        String stepName,
        ApprovalStepAssigneeRule assigneeRule,
        String assigneeValue,
        Integer timeoutHours,
        RejectStrategy rejectStrategy,
        String description) {
}
