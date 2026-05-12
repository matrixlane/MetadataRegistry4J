package io.github.matrixlane.metadataregistry.web.dto.registrar;

import java.util.UUID;

public record RegistrarResponse(
        UUID id,
        String identifier,
        UUID registrationAuthorityId,
        String registrationAuthorityName,
        UUID userId,
        UUID organizationId,
        String organizationName,
        UUID individualId,
        String individualName) {
}
