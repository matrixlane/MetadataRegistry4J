package io.github.matrixlane.metadataregistry.web.dto.contact;

import java.util.UUID;

public record ContactResponse(
        UUID id,
        UUID organizationId,
        String organizationName,
        UUID individualId,
        String individualName,
        UUID roleId,
        String roleName) {
}
