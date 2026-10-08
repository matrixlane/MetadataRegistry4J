package io.github.matrixlane.metadataregistry.web.dto.registries;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.DegreeOfConformance;

import java.util.List;
import java.util.UUID;

public record RegistrySpecificationResponse(
        UUID id,
        String name,
        String comment,
        String webAddress,
        UUID defaultLanguageId,
        String defaultLanguageIdentifier,
        List<UUID> alternativeLanguageIds,
        String characterRepertoire,
        List<String> referenceDocumentIdentifierKinds,
        String representationClassSchemeId,
        UUID contextId,
        String contextLabel,
        String standard,
        DegreeOfConformance degreeOfConformance,
        String conformanceLevel,
        String conformanceLabel,
        String includedFeatures,
        String excludedFeatures,
        String defaultTimeZone) {
}
