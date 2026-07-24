package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

public enum SystemRole {
    SUPER_ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
