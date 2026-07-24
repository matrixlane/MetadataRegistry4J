package io.github.matrixlane.metadataregistry.web.dto.registrationauthority;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record RegistrationAuthorityRequest(
        @NotBlank(message = "name 不能为空")
        String name,
        String englishName,
        String mailAddress,
        List<String> emailAddress,
        List<String> phoneNumber,
        String webAddress,
        String internationalCodeDesignator,
        String organizationIdentifier,
        String organizationPartIdentifier,
        String opiSource,
        UUID namespaceId,
        List<UUID> documentationLanguageIds) {
}
