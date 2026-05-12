package io.github.matrixlane.metadataregistry.web.dto.designationdefinition;

import java.util.UUID;

public record DesignationDefinitionPairingResponse(
        UUID id,
        UUID designationId,
        String designationLabel,
        UUID definitionId,
        String definitionLabel,
        UUID contextId,
        String contextLabel,
        String acceptability) {
}
