package io.github.matrixlane.metadataregistry.web.dto.namespace;

import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.util.List;
import java.util.UUID;

/**
 * Namespace response DTO with full ISO 11179-3 attributes.
 *
 * @param id Namespace UUID
 * @param label Preferred designation as human-readable label
 * @param namingAuthorityId Organization ID that has naming authority in this namespace
 * @param namingAuthorityName Organization name that has naming authority in this namespace
 * @param designations All designations (names) in multiple languages
 * @param definitions All definitions in multiple languages
 * @param shorthandPrefix Quick qualifier prefix
 * @param schemeReference IRI-style scheme reference
 * @param mandatoryNamingConventionIndicator Naming convention enforcement flag
 * @param namingConventionIds Bound naming conventions (if any)
 * @param oneNamePerItemIndicator One name per Item flag
 * @param oneItemPerNameIndicator One Item per name flag
 * @param identifierInputMode Identifier input mode (AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY)
 * @param versionInputMode Version input mode (AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY)
 * @param versioningPolicy Version numbering policy (SEMVER / SEQUENTIAL / DATE)
 * @param manualIdentifierPattern Regex applied to manual identifiers (may be null)
 */
public record NamespaceDetailResponse(
        UUID id,
        String label,
        UUID namingAuthorityId,
        String namingAuthorityName,
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
        Integer strategySeqPad,
        /** Namespace kind relative to the caller: OWN (自有) or EXTERNAL (外部). */
        String namespaceKind) {
}
