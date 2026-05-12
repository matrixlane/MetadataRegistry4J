package io.github.matrixlane.metadataregistry.web.dto.approval;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartApprovalRequest(
        @NotNull UUID administeredItemId,
        @NotNull UUID templateId) {
}
