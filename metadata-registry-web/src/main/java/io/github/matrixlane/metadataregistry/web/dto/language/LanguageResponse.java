package io.github.matrixlane.metadataregistry.web.dto.language;

import java.util.List;
import java.util.UUID;

public record LanguageResponse(
        UUID id,
        String languageIdentifier,
        String scriptIdentifier,
        String geopoliticalTerritoryIdentifier,
        String languageName,
        String geopoliticalTerritoryName,
        List<String> variantIdentifier,
        List<String> extensionIdentifier,
        String privateUseQualifier,
        Boolean enabled) {
}
