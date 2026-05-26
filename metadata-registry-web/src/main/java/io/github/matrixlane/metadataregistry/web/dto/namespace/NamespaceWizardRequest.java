package io.github.matrixlane.metadataregistry.web.dto.namespace;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

/**
 * Namespace creation wizard request DTO.
 * 
 * <p>Supports the WI-028-A Namespace creation workflow with ISO 11179-3 attributes.</p>
 *
 * @param namingAuthorityId Required. RegistrationAuthority that governs this namespace.
 * @param designations Required. At least one designation (name) must be provided.
 * @param definitions Optional. Multiple language definitions supported.
 * @param registration Optional. Registration metadata for ISO state management.
 * @param parentId Optional. Parent namespace ID for hierarchical structure.
 * @param shorthandPrefix Optional. Quick qualifier prefix (e.g., "hl7:", "iso:").
 * @param schemeReference Optional. IRI-style scheme reference (e.g., "urn:example:ns").
 * @param mandatoryNamingConventionIndicator When TRUE, a NamingConvention must be bound.
 * @param namingConventionId Required when mandatoryNamingConventionIndicator=TRUE.
 * @param oneNamePerItemIndicator When TRUE, only one name per Item allowed.
 * @param oneItemPerNameIndicator When TRUE, only one Item per name allowed (default TRUE).
 */
public record NamespaceWizardRequest(
        UUID namingAuthorityId,
        @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        ItemRegistrationRequest registration,
        UUID parentId,
        String shorthandPrefix,
        String schemeReference,
        Boolean mandatoryNamingConventionIndicator,
        UUID namingConventionId,
        Boolean oneNamePerItemIndicator,
        Boolean oneItemPerNameIndicator) {
}