package io.github.matrixlane.metadataregistry.web.dto.designationdefinition;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DefinitionRequest(
        @NotNull(message = "itemId 不能为空")
        UUID itemId,
        UUID languageId,
        @NotBlank(message = "value 不能为空")
        String value,
        UUID sourceId,
        UUID contextId,
        @NotNull(message = "acceptability 不能为空")
        Acceptability acceptability) {
}
