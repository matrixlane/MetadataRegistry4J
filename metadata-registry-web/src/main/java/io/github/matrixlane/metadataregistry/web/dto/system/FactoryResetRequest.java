package io.github.matrixlane.metadataregistry.web.dto.system;

import jakarta.validation.constraints.NotBlank;

public record FactoryResetRequest(
    @NotBlank(message = "密码不能为空")
    String password
) {}
