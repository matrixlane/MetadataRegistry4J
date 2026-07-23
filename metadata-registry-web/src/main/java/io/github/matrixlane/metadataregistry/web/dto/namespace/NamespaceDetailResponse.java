package io.github.matrixlane.metadataregistry.web.dto.namespace;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
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
 * @param meta Item metadata (creation, modification, registration state)
 * @param shorthandPrefix Quick qualifier prefix
 * @param schemeReference IRI-style scheme reference
 * @param mandatoryNamingConventionIndicator Naming convention enforcement flag
 * @param namingConventionIds Bound naming conventions (if any)
 * @param oneNamePerItemIndicator One name per Item flag
 * @param oneItemPerNameIndicator One Item per name flag
 * @param parentId Parent namespace ID (null for root namespaces)
 * @param parentLabel Parent namespace preferred designation
 * @param depth Hierarchical depth (0 for root, 1+ for children)
 */
public record NamespaceDetailResponse(
        UUID id,
        String label,
        UUID namingAuthorityId,
        String namingAuthorityName,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta,
        String shorthandPrefix,
        String schemeReference,
        Boolean mandatoryNamingConventionIndicator,
        List<UUID> namingConventionIds,
        Boolean oneNamePerItemIndicator,
        Boolean oneItemPerNameIndicator,
        UUID parentId,
        String parentLabel,
        Integer depth) {
}
