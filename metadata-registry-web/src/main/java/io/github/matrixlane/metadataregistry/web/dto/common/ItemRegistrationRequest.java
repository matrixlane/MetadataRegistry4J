package io.github.matrixlane.metadataregistry.web.dto.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ItemRegistrationRequest(
        UUID namespaceId,
        String identifier,
        String version,
        UUID registrationAuthorityId,
        @NotEmpty(message = "designations 不能为空") @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        /** Segment-template VARIABLE values supplied by the registration form. */
        Map<String, String> variables) {

    /** Backward-compatible constructor without segment variables. */
    public ItemRegistrationRequest(UUID namespaceId, String identifier, String version,
                                   UUID registrationAuthorityId,
                                   List<TextLine> designations, List<TextLine> definitions) {
        this(namespaceId, identifier, version, registrationAuthorityId, designations, definitions, null);
    }
}
