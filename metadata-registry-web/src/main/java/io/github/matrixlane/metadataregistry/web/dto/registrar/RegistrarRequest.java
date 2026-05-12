package io.github.matrixlane.metadataregistry.web.dto.registrar;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record RegistrarRequest(
        @NotBlank(message = "标识符不能为空") String identifier,
        UUID registrationAuthorityId,
        UUID userId,
        UUID organizationId,
        UUID individualId) {
}
