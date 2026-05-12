package io.github.matrixlane.metadataregistry.web.dto.common;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;

import java.util.UUID;

public record ItemMetaResponse(
        UUID itemId,
        String preferredDesignation,
        String preferredDefinition,
        String scopedIdentifier,
        UUID namespaceId,
        String namespaceLabel,
        String identifier,
        String version,
        RegistrationStatus registrationStatus,
        UUID administeredItemId) {
}
