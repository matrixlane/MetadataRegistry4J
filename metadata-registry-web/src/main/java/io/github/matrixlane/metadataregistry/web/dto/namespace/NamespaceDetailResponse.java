package io.github.matrixlane.metadataregistry.web.dto.namespace;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.NamespaceStatus;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.util.List;
import java.util.UUID;

/**
 * Namespace response DTO with full ISO 11179-3 attributes.
 *
 * <p>Extended from base NamespaceResponse to include:</p>
 * <ul>
 *   <li>ISO governance attributes (shorthandPrefix, schemeReference, indicators)</li>
 *   <li>NamespaceStatus for lifecycle tracking</li>
 *   <li>Parent namespace for hierarchical support</li>
 * </ul>
 *
 * @param id Namespace UUID
 * @param label Preferred designation as human-readable label
 * @param namingAuthorityId RegistrationAuthority ID
 * @param namingAuthorityName RegistrationAuthority name
 * @param designations All designations (names) in multiple languages
 * @param definitions All definitions in multiple languages
 * @param meta Item metadata (creation, modification, registration state)
 * @param shorthandPrefix Quick qualifier prefix
 * @param schemeReference IRI-style scheme reference
 * @param mandatoryNamingConventionIndicator Naming convention enforcement flag
 * @param namingConventionId Bound naming convention (if any)
 * @param oneNamePerItemIndicator One name per Item flag
 * @param oneItemPerNameIndicator One Item per name flag
 * @param namespaceStatus Current lifecycle status
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
        UUID namingConventionId,
        Boolean oneNamePerItemIndicator,
        Boolean oneItemPerNameIndicator,
        NamespaceStatus namespaceStatus,
        UUID parentId,
        String parentLabel,
        Integer depth) {
}