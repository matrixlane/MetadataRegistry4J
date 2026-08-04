package io.github.matrixlane.metadataregistry.web.dto.namespace;

import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.util.List;
import java.util.UUID;

public record NamespaceResponse(
        UUID id,
        String label,
        UUID namingAuthorityId,
        String namingAuthorityName,
        UUID registrationAuthorityId,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        String shorthandPrefix,
        String schemeReference,
        Boolean mandatoryNamingConventionIndicator,
        List<UUID> namingConventionIds,
        Boolean oneNamePerItemIndicator,
        Boolean oneItemPerNameIndicator,
        String identifierInputMode,
        String versionInputMode,
        String versioningPolicy,
        String manualIdentifierPattern,
        String identifierStrategy,
        String strategyPrefix,
        String strategyTemplate,
        Long strategySeqStart,
        Integer strategySeqPad) {
}
