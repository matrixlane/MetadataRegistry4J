package io.github.matrixlane.metadataregistry.web.dto.common;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import java.util.List;
import java.util.UUID;

public record TextLineResponse(
        UUID id,
        UUID languageId,
        String languageCode,
        String value,
        UUID sourceId,
        String sourceTitle,
        List<TextLineContextResponse> contexts,
        Acceptability acceptability) {
}
