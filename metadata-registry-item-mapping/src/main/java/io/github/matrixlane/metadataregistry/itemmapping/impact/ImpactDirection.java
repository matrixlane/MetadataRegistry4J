package io.github.matrixlane.metadataregistry.itemmapping.impact;

/**
 * Direction of impact for a mapping relationship.
 */
public enum ImpactDirection {
    /** The item is the source (subject) of the impact. */
    SOURCE_AFFECTED,
    /** The item is the target (object) of the impact. */
    TARGET_AFFECTED,
    /** The item is affected in both directions (e.g. equivalent mappings). */
    BOTH
}
