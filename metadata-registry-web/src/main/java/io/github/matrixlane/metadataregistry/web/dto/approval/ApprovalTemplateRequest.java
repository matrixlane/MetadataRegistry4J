package io.github.matrixlane.metadataregistry.web.dto.approval;

import io.github.matrixlane.metadataregistry.web.entity.RejectStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ApprovalTemplateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull @Min(2) Integer stepCount,
        Integer defaultTimeoutHours,
        RejectStrategy defaultRejectStrategy,
        @NotNull @Size(min = 2) @Valid List<ApprovalStepRequest> steps) {
}
