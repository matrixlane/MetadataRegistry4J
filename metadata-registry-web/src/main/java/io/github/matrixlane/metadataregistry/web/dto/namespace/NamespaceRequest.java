package io.github.matrixlane.metadataregistry.web.dto.namespace;

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
