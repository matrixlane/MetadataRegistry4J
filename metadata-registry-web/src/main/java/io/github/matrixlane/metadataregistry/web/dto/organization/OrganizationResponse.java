package io.github.matrixlane.metadataregistry.web.dto.organization;

import java.util.List;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        List<String> additionalNames,
        String mailAddress,
        List<String> emailAddress,
        List<String> phoneNumber,
        String webAddress) {
}
