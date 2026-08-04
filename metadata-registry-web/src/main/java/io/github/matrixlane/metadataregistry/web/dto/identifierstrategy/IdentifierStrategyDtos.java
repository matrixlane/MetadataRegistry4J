package io.github.matrixlane.metadataregistry.web.dto.identifierstrategy;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** DTOs for the segment-template identifier strategy admin API. */
public final class IdentifierStrategyDtos {

    private IdentifierStrategyDtos() {
    }

    /** One persisted strategy configuration version. */
    public record StrategyConfigResponse(
            Long id,
            String strategyType,
            String configJson,
            Integer version,
            String status,
            Boolean isGlobalDefault,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil) {
    }

    /** Namespace strategy view: published config + drafts. */
    public record NamespaceStrategyView(
            StrategyConfigResponse published,
            List<StrategyConfigResponse> drafts,
            List<StrategyConfigResponse> history) {
    }

    /** Save-draft / publish-inline request body. */
    public record SaveTemplateRequest(
            @NotBlank(message = "configJson 不能为空")
            String configJson) {
    }

    /** Preview request: template + sample variables, no allocation. */
    public record PreviewRequest(
            @NotBlank(message = "configJson 不能为空")
            String configJson,
            String itemType,
            Map<String, String> variables) {
    }

    public record PreviewResponse(
            String sample,
            String formatRegex,
            List<String> validationErrors) {
    }

    /** Format validation request: template + candidate identifier. */
    public record ValidateFormatRequest(
            @NotBlank(message = "configJson 不能为空")
            String configJson,
            @NotBlank(message = "identifier 不能为空")
            String identifier) {
    }

    public record ValidateFormatResponse(
            boolean matches,
            Map<String, String> parsedValues,
            String error) {
    }

    /** Backfill result summary. */
    public record BackfillResponse(
            int parsed,
            int skipped,
            Map<String, Long> scopeMaxValues) {
    }

    /** Designer preset descriptor. */
    public record PresetResponse(
            String key,
            String name,
            String description,
            String configJson) {
    }
}
