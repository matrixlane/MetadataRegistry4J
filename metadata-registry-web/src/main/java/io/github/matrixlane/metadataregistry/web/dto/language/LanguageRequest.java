package io.github.matrixlane.metadataregistry.web.dto.language;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record LanguageRequest(
        @NotBlank(message = "languageIdentifier 不能为空")
        String languageIdentifier,
        String scriptIdentifier,
        String geopoliticalTerritoryIdentifier,
        List<String> variantIdentifier,
        List<String> extensionIdentifier,
        String privateUseQualifier) {
}
