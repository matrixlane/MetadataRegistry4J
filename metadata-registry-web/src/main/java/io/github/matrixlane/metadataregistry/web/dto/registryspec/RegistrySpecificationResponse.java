package io.github.matrixlane.metadataregistry.web.dto.registryspec;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import java.util.UUID;

public record RegistrySpecificationResponse(
    UUID id,
    String registryName,
    String identifierPrefix,
    Boolean autoGenerateIdentifier,
    String identifierPattern,
    String versioningPolicy,
    Boolean mandatoryClassification,
    String defaultLanguage,
    String description
) {
    public static RegistrySpecificationResponse from(RegistrySpecification entity) {
        if (entity == null) return null;
        return new RegistrySpecificationResponse(
                entity.getId(),
                entity.getRegistryName(),
                entity.getIdentifierPrefix(),
                entity.getAutoGenerateIdentifier(),
                entity.getIdentifierPattern(),
                entity.getVersioningPolicy(),
                entity.getMandatoryClassification(),
                entity.getDefaultLanguage(),
                entity.getDescription()
        );
    }
}
