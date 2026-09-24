package io.github.matrixlane.metadataregistry.web.dto.registrar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RegistrarRequest(
        @NotBlank(message = "标识符不能为空") String identifier,
        UUID registrationAuthorityId,
        UUID userId,
        @NotNull(message = "所属组织不能为空") UUID organizationId,
        UUID individualId,
        UUID roleId) {
}
