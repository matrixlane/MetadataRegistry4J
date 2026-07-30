package io.github.matrixlane.metadataregistry.web.dto.namingconvention;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.util.List;
import java.util.UUID;

public record NamingConventionResponse(
        UUID id,
        String label,
        String scopeRule,
        String authorityRule,
        String semanticRule,
        String syntacticRule,
        String lexicalRule,
        String rulesJson,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta) {
}