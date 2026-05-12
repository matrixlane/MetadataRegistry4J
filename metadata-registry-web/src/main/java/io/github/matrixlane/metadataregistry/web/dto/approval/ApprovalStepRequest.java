package io.github.matrixlane.metadataregistry.web.dto.approval;

import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepAssigneeRule;
import io.github.matrixlane.metadataregistry.web.entity.RejectStrategy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApprovalStepRequest(
        @NotNull @Min(1) Integer stepNumber,
        @NotBlank String stepName,
        @NotNull ApprovalStepAssigneeRule assigneeRule,
        @NotBlank String assigneeValue,
        Integer timeoutHours,
        RejectStrategy rejectStrategy,
        String description) {
}
