package io.github.matrixlane.metadataregistry.web.dto.approval;

import io.github.matrixlane.metadataregistry.web.entity.RejectStrategy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ApprovalTemplateResponse(
        UUID id,
        String name,
        String description,
        Integer stepCount,
        Boolean enabled,
        Boolean locked,
        Integer defaultTimeoutHours,
        RejectStrategy defaultRejectStrategy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ApprovalStepResponse> steps) {
}
