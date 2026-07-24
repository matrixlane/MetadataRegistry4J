package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String username,
        SystemRole role,
        boolean forceChangePassword,
        boolean emergencyLogin,
        boolean tempPasswordUsed) {

    public AuthenticatedUser(UUID id, String username, SystemRole role) {
        this(id, username, role, false, false, false);
    }
}
