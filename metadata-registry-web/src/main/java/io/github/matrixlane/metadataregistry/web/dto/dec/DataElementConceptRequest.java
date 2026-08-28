package io.github.matrixlane.metadataregistry.web.dto.dec;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

public record DataElementConceptRequest(
        UUID objectClassId,
        UUID propertyId,
        String sourceUri,
        List<UUID> conceptualDomainIds,
        @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        ItemRegistrationRequest registration) {
}
