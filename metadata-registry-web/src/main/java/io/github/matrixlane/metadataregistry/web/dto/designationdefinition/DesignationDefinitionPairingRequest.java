package io.github.matrixlane.metadataregistry.web.dto.designationdefinition;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DesignationDefinitionPairingRequest(
        @NotNull(message = "designationId 不能为空")
        UUID designationId,
        @NotNull(message = "definitionId 不能为空")
        UUID definitionId,
        UUID contextId,
        @NotNull(message = "acceptability 不能为空")
        Acceptability acceptability) {
}
