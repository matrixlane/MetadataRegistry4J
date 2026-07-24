package io.github.matrixlane.metadataregistry.web.dto.administereditem;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AdministeredItemResponse(
        UUID id,
        UUID itemId,
        String itemType,
        String itemLabel,
        String identifier,
        String version,
        UUID namespaceId,
        String namespaceLabel,
        UUID registrationAuthorityId,
        String registrationAuthorityLabel,
        RegistrationStatus registrationStatus,
        LocalDateTime stateEffectiveDate,
        String administrativeNote,
        UUID constraintSetId,
        String constraintSetLabel,
        List<UUID> referenceDocumentIds,
        LocalDateTime creationDateTime,
        LocalDateTime lastChangeDateTime,
        String changeDescription) {
}
