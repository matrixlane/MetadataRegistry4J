package io.github.matrixlane.metadataregistry.itemmapping.enums;

/**
 * Lifecycle status of an interoperability-to-local mapping result.
 *
 * <p>Tracks the confirmation state of a match suggestion produced by the
 * matching engine (WI-013-2a).
 *
 * <ul>
 *   <li>{@link #AUTO_MATCHED} — high-confidence match (>0.85) applied automatically</li>
 *   <li>{@link #PENDING} — medium-confidence match (0.60~0.85) awaiting manual confirmation</li>
 *   <li>{@link #CONFIRMED} — manually confirmed by a data steward</li>
 *   <li>{@link #REJECTED} — explicitly rejected by a data steward</li>
 * </ul>
 */
public enum InteropMappingStatus {

    /**
     * High-confidence match automatically applied by the matching engine.
     * No manual intervention required.
     */
    AUTO_MATCHED,

    /**
     * Medium-confidence match awaiting manual review and confirmation.
     */
    PENDING,

    /**
     * Match has been manually confirmed by a data steward.
     */
    CONFIRMED,

    /**
     * Match has been explicitly rejected by a data steward.
     */
    REJECTED
}
