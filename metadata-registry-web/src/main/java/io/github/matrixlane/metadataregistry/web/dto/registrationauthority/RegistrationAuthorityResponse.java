package io.github.matrixlane.metadataregistry.web.dto.registrationauthority;

import java.util.List;
import java.util.UUID;

public record RegistrationAuthorityResponse(
        UUID id,
        String name,
        String mailAddress,
        List<String> emailAddress,
        List<String> phoneNumber,
        String webAddress,
        String internationalCodeDesignator,
        String organizationIdentifier,
        String organizationPartIdentifier,
        String opiSource,
        UUID namespaceId,
        String namespaceLabel,
        List<UUID> documentationLanguageIds,
        List<String> documentationLanguages) {
}
