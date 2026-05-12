package io.github.matrixlane.metadataregistry.web.auth;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String username, AppUserRole role) {
}
