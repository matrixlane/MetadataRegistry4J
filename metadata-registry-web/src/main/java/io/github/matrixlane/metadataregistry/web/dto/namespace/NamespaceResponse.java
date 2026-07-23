package io.github.matrixlane.metadataregistry.web.dto.namespace;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.util.List;
import java.util.UUID;

public record NamespaceResponse(
        UUID id,
        String label,
        UUID namingAuthorityId,
        String namingAuthorityName,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta,
        String shorthandPrefix,
        String schemeReference,
        Boolean mandatoryNamingConventionIndicator,
        List<UUID> namingConventionIds,
        Boolean oneNamePerItemIndicator,
        Boolean oneItemPerNameIndicator,
        UUID parentId) {
}
