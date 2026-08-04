package io.github.matrixlane.metadataregistry.web.service.identifier;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierInputMode;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.VersioningPolicy;

/**
 * Strongly-typed view of a Namespace's identifier governance policy,
 * resolved from its persisted columns.
 */
public record NamespaceIdentifierPolicy(
        IdentifierInputMode inputMode,
        IdentifierInputMode versionInputMode,
        VersioningPolicy versioningPolicy,
        String manualIdentifierPattern) {

    /** Defaults used when a namespace has no explicit policy settings. */
    public static final NamespaceIdentifierPolicy DEFAULT = new NamespaceIdentifierPolicy(
            IdentifierInputMode.AUTO_WITH_OVERRIDE, IdentifierInputMode.AUTO_WITH_OVERRIDE,
            VersioningPolicy.SEMVER, null);
}
