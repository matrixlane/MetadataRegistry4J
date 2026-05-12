package io.github.matrixlane.metadataregistry.web.dto.individual;

import java.util.List;
import java.util.UUID;

public record IndividualResponse(
        UUID id,
        String name,
        String title,
        String mailAddress,
        List<String> emailAddress,
        List<String> phoneNumber) {
}
