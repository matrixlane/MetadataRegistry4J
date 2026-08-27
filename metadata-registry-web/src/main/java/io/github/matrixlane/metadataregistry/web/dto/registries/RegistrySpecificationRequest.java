package io.github.matrixlane.metadataregistry.web.dto.registries;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.DegreeOfConformance;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record RegistrySpecificationRequest(
        @NotBlank(message = "name 不能为空")
        String name,
        String comment,
        String webAddress,
        UUID defaultLanguageId,
        List<UUID> alternativeLanguageIds,
        @NotBlank(message = "characterRepertoire 不能为空")
        String characterRepertoire,
        List<String> referenceDocumentIdentifierKinds,
        String representationClassSchemeId,
        UUID contextId,
        String standard,
        DegreeOfConformance degreeOfConformance,
        String conformanceLevel,
        String conformanceLabel,
        String includedFeatures,
        String excludedFeatures,
        String defaultTimeZone,
        @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        ItemRegistrationRequest registration) {
}
