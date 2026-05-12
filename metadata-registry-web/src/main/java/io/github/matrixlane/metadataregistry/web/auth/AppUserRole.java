package io.github.matrixlane.metadataregistry.web.auth;

public enum AppUserRole {
    ADMIN,
    REGISTRAR,
    VIEWER;

    public String authority() {
        return "ROLE_" + name();
    }
}
