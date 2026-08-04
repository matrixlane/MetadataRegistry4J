package io.github.matrixlane.metadataregistry.web.dto.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ItemRegistrationRequest(
        UUID namespaceId,
        String identifier,
        String version,
        @NotEmpty(message = "designations 不能为空") @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions) {
}
