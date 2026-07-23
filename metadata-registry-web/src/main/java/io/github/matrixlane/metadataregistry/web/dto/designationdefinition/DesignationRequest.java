package io.github.matrixlane.metadataregistry.web.dto.designationdefinition;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record DesignationRequest(
        @NotNull(message = "itemId 不能为空")
        UUID itemId,
        @NotNull(message = "languageId 不能为空")
        UUID languageId,
        @NotBlank(message = "value 不能为空")
        String value,
        UUID sourceId,
        UUID namespaceId,
        List<UUID> namingConventionIds,
        @Valid
        List<ContextEntry> contexts) {

    public record ContextEntry(
            @NotNull(message = "contextId 不能为空")
            UUID contextId,
            @NotNull(message = "acceptability 不能为空")
            Acceptability acceptability) {
    }
}
