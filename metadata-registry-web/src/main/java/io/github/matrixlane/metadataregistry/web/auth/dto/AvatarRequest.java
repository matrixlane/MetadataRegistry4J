package io.github.matrixlane.metadataregistry.web.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AvatarRequest(
        @NotBlank(message = "dataUrl 不能为空")
        String dataUrl) {
}
