package io.github.matrixlane.metadataregistry.web.dto.scopedidentifier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ScopedIdentifierCreateRequest(
        @NotNull(message = "namespaceId 不能为空")
        UUID namespaceId,

        String identifier,

        @Pattern(regexp = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$",
                message = "版本号格式必须为 major.minor.patch")
        String version,

        @NotNull(message = "identifiedItemId 不能为空")
        UUID identifiedItemId,

        @Size(max = 1024, message = "变更摘要长度不能超过 1024")
        String changeSummary
) {
}
