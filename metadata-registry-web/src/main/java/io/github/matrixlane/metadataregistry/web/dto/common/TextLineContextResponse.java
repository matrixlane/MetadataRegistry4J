package io.github.matrixlane.metadataregistry.web.dto.common;

import java.util.UUID;

public record TextLineContextResponse(
        UUID contextId,
        String contextLabel,
        String acceptability) {
}
