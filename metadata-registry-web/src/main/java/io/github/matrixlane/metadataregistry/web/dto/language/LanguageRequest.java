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
        String privateUseQualifier,
        Boolean enabled,
        // 语言名称/地区名称(可选):仅来自请求或扩展表持久化值,留空时存 null,不做运行时解析
        String languageName,
        String territoryName) {
}
