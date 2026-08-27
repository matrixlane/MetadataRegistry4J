package io.github.matrixlane.metadataregistry.web.dto.namingconvention;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record NamingConventionRequest(
        @NotBlank(message = "scopeRule 不能为空")
        String scopeRule,
        String authorityRule,
        String semanticRule,
        String syntacticRule,
        String lexicalRule,
        String rulesJson,
        @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        ItemRegistrationRequest registration) {
}