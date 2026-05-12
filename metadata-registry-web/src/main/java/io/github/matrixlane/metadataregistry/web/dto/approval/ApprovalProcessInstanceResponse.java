package io.github.matrixlane.metadataregistry.web.dto.approval;

import io.github.matrixlane.metadataregistry.web.entity.ApprovalProcessStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ApprovalProcessInstanceResponse(
        UUID id,
        UUID templateId,
        String templateName,
        UUID administeredItemId,
        String submitter,
        Integer currentStep,
        ApprovalProcessStatus status,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        List<ApprovalStepRecordResponse> stepRecords) {
}
