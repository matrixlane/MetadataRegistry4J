package io.github.matrixlane.metadataregistry.ext.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
/**
 * Version numbering policy of a {@link Namespace}.
 *
 * <p>Persisted as the {@code versioning_policy} column on the {@code namespace}
 * table. When unset, the effective policy is {@link #SEMVER}.
 */
public enum VersioningPolicy {

    /**
     * Semantic versioning (major.minor.patch). Initial version: {@code 1.0.0}.
     */
    SEMVER,

    /**
     * Sequential integer versioning (1, 2, 3, ...). Initial version: {@code 1}.
     */
    SEQUENTIAL,

    /**
     * Date-based versioning (yyyy-MM-dd). Initial version: current date.
     */
    DATE
}
