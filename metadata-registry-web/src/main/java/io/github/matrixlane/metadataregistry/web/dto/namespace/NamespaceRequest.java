package io.github.matrixlane.metadataregistry.web.dto.namespace;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record NamespaceRequest(
        UUID namingAuthorityId,
        @NotEmpty(message = "designations 不能为空") @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        ItemRegistrationRequest registration) {
}
