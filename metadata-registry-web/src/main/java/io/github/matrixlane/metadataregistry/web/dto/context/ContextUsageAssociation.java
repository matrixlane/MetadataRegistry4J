package io.github.matrixlane.metadataregistry.web.dto.context;

import java.util.UUID;

public record ContextUsageAssociation(
        String associationType,
        UUID designationId,
        String designationValue,
        UUID definitionId,
        String definitionValue,
        String acceptability) {
}
