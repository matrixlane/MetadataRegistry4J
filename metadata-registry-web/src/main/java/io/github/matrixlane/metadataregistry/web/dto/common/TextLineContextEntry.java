package io.github.matrixlane.metadataregistry.web.dto.common;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TextLineContextEntry(
        @NotNull(message = "contextId 不能为空")
        UUID contextId,
        @NotNull(message = "acceptability 不能为空")
        Acceptability acceptability) {
}
