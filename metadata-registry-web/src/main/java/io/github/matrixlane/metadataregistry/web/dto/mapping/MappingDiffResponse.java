package io.github.matrixlane.metadataregistry.web.dto.mapping;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for mapping scheme version diff.
 *
 * <p>Contains structured differences between two scheme versions:
 * added, removed, and modified mappings.
 */
public record MappingDiffResponse(

        /**
         * ID of the base (older) scheme version.
         */
        UUID baseId,

        /**
         * Version string of the base scheme.
         */
        String baseVersion,

        /**
         * ID of the compare (newer) scheme version.
         */
        UUID compareId,

        /**
         * Version string of the compare scheme.
         */
        String compareVersion,

        /**
         * Mappings present in compare but not in base (new additions).
         */
        List<MappingDiffEntry> added,

        /**
         * Mappings present in base but not in compare (deletions).
         */
        List<MappingDiffEntry> removed,

        /**
         * Mappings present in both but with different attributes.
         */
        List<MappingDiffEntry> modified,

        /**
         * Total count of differences (added + removed + modified).
         */
        int totalDiffCount,

        /**
         * True if both versions are identical (no differences).
         */
        boolean identical
) {
}
