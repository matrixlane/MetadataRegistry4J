package io.github.matrixlane.metadataregistry.web.dto.approval;

public record TransferStepRequest(
        String newAssignee,
        String newAssigneeRole) {
}
