package io.github.matrixlane.metadataregistry.itemmapping.dto;

import java.util.List;
import java.util.UUID;

/**
 * Warning produced when a single local {@code AdministeredItem} is mapped
 * to multiple FHIR Elements.
 *
 * <p>Per WI-012-2 conflict detection requirement.
 *
 * @param localItemId      the local item UUID involved in the conflict
 * @param conflictingPaths list of FHIR paths that map to the same local item
 * @param warningMessage   human-readable warning description
 */
public record FhirMappingConflictWarning(
        UUID localItemId,
        List<String> conflictingPaths,
        String warningMessage
) {

    /**
     * Creates a conflict warning with an auto-generated message.
     */
    public static FhirMappingConflictWarning of(UUID localItemId,
                                                 List<String> conflictingPaths) {
        String message = String.format(
                "Local item %s is mapped to %d FHIR Elements: %s",
                localItemId,
                conflictingPaths.size(),
                String.join(", ", conflictingPaths)
        );
        return new FhirMappingConflictWarning(localItemId, conflictingPaths, message);
    }
}
