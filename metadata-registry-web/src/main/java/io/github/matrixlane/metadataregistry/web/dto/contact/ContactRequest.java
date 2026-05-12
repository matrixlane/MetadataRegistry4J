package io.github.matrixlane.metadataregistry.web.dto.contact;

import java.util.UUID;

public record ContactRequest(
        UUID organizationId,
        UUID individualId,
        UUID roleId) {
}
