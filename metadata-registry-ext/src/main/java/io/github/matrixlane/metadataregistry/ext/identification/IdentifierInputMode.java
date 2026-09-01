package io.github.matrixlane.metadataregistry.ext.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
/**
 * Identifier input governance mode of a {@link Namespace}.
 *
 * <p>Persisted as the {@code identifier_input_mode} / {@code version_input_mode}
 * columns on the {@code namespace} table. When unset, the effective mode is
 * {@link #AUTO_WITH_OVERRIDE}.
 */
public enum IdentifierInputMode {

    /**
     * Auto-generation on, manual editing forbidden. Requests carrying a
     * non-blank identifier are rejected.
     */
    AUTO_ONLY,

    /**
     * Auto-generation on, manual override allowed. Blank identifier triggers
     * generation; non-blank identifier is validated and used as-is.
     */
    AUTO_WITH_OVERRIDE,

    /**
     * Auto-generation off, manual entry required. Requests with a blank
     * identifier are rejected.
     */
    MANUAL_ONLY
}
