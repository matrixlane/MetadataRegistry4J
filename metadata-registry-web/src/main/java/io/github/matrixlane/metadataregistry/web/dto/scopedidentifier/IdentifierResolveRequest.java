package io.github.matrixlane.metadataregistry.web.dto.scopedidentifier;

import jakarta.validation.constraints.NotBlank;

public record IdentifierResolveRequest(
        @NotBlank(message = "identifier 不能为空")
        String identifier,

        String version
) {
}
