package io.github.matrixlane.metadataregistry.web.dto.documenttype;

import jakarta.validation.constraints.NotBlank;

public record DocumentTypeRequest(
        @NotBlank(message = "identifier 不能为空") String identifier,
        String description,
        String schemeReference) {
}
