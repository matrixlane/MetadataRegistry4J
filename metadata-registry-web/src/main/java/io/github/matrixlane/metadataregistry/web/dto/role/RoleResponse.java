package io.github.matrixlane.metadataregistry.web.dto.role;

import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String roleName,
        String mailAddress,
        List<String> emailAddress,
        List<String> phoneNumber,
        long contactCount,
        boolean inUse) {
}
