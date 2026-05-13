package io.github.matrixlane.metadataregistry.web.dto.mapping;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;

import java.util.List;
import java.util.UUID;

/**
 * Represents a single mapping difference entry.
 *
 * <p>Used for added, removed, and modified mappings between two scheme versions.
 *
 * <h3>Added / Removed</h3>
 * <p>Only {@code source}, {@code target}, {@code mappingType}, {@code description},
 * and {@code transformationRule} are populated.</p>
 *
 * <h3>Modified</h3>
 * <p>{@code source} and {@code target} identify the mapping pair.
 * {@code from} and {@code to} contain the before/after snapshots,
 * and {@code changedFields} lists which fields differ.</p>
 */
public record MappingDiffEntry(

        /**
         * The source (subject) identifier: either an item UUID or external URI.
         */
        String source,

        /**
         * The target (object) identifier: either an item UUID or external URI.
         */
        String target,

        /**
         * The mapping type (e.g. EQUIVALENT, DERIVED).
         */
        MappingType mappingType,

        /**
         * Human-readable description of the mapping.
         */
        String description,

        /**
         * Optional transformation rule.
         */
        String transformationRule,

        /**
         * For modified entries: the before snapshot.
         */
        MappingSnapshot from,

        /**
         * For modified entries: the after snapshot.
         */
        MappingSnapshot to,

        /**
         * For modified entries: list of changed field names.
         * E.g. ["mappingType", "description"]
         */
        List<String> changedFields
) {

    /**
     * Creates an entry for an added or removed mapping.
     */
    public static MappingDiffEntry of(
            String source,
            String target,
            MappingType mappingType,
            String description,
            String transformationRule) {
        return new MappingDiffEntry(source, target, mappingType, description,
                transformationRule, null, null, null);
    }

    /**
     * Creates an entry for a modified mapping.
     */
    public static MappingDiffEntry ofModified(
            String source,
            String target,
            MappingSnapshot from,
            MappingSnapshot to,
            List<String> changedFields) {
        return new MappingDiffEntry(source, target, null, null, null,
                from, to, changedFields);
    }

    /**
     * Snapshot of a mapping's mutable fields for comparison.
     */
    public record MappingSnapshot(
            MappingType mappingType,
            String description,
            String transformationRule
    ) {
    }
}
