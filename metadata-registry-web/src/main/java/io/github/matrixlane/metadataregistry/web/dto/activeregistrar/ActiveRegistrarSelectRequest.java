package io.github.matrixlane.metadataregistry.web.dto.activeregistrar;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ActiveRegistrarSelectRequest(
        @NotNull(message = "registrarId 不能为空") UUID registrarId) {
}
