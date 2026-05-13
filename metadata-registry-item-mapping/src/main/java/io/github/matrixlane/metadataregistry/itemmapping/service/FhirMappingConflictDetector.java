package io.github.matrixlane.metadataregistry.itemmapping.service;

import io.github.matrixlane.metadataregistry.itemmapping.dto.FhirMappingConflictWarning;
import io.github.matrixlane.metadataregistry.itemmapping.entity.FhirElementMapping;
import io.github.matrixlane.metadataregistry.itemmapping.repository.FhirElementMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Detects mapping conflicts where a single local {@code AdministeredItem}
 * is mapped to multiple FHIR Elements.
 *
 * <p>Per WI-012-2 — conflict detection must complete in ≤ 100 ms
 * for 1 000 mappings.
 */
@Component
public class FhirMappingConflictDetector {

    private static final Logger log = LoggerFactory.getLogger(FhirMappingConflictDetector.class);

    private final FhirElementMappingRepository repository;

    public FhirMappingConflictDetector(FhirElementMappingRepository repository) {
        this.repository = repository;
    }

    /**
     * Detects conflicts for a specific local item.
     *
     * <p>A conflict is defined as: the same {@code localItemId} mapped to
     * more than one distinct {@code fhirPath}.
     *
     * @param localItemId the local item UUID to check
     * @return a conflict warning if multiple mappings exist, otherwise empty
     */
    public List<FhirMappingConflictWarning> detectConflicts(UUID localItemId) {
        List<FhirElementMapping> mappings = repository.findByLocalItemId(localItemId);
        return analyzeMappings(localItemId, mappings);
    }

    /**
     * Detects all conflicts across the entire mapping registry.
     *
     * @return list of all conflict warnings
     */
    public List<FhirMappingConflictWarning> detectAllConflicts() {
        long start = System.currentTimeMillis();

        List<UUID> conflictingIds = repository.findLocalItemIdsWithMultipleMappings();
        if (conflictingIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<FhirElementMapping> allConflictingMappings =
                repository.findByLocalItemIds(conflictingIds);

        Map<UUID, List<FhirElementMapping>> grouped = allConflictingMappings.stream()
                .collect(Collectors.groupingBy(FhirElementMapping::getLocalItemId));

        List<FhirMappingConflictWarning> warnings = new ArrayList<>();
        for (Map.Entry<UUID, List<FhirElementMapping>> entry : grouped.entrySet()) {
            warnings.addAll(analyzeMappings(entry.getKey(), entry.getValue()));
        }

        long elapsed = System.currentTimeMillis() - start;
        log.debug("Conflict detection completed in {} ms for {} items",
                elapsed, conflictingIds.size());

        return warnings;
    }

    /**
     * Detects conflicts scoped to a specific mapping scheme.
     *
     * @param mappingSchemeId the scheme UUID
     * @return list of conflict warnings within the scheme
     */
    public List<FhirMappingConflictWarning> detectConflictsByScheme(UUID mappingSchemeId) {
        List<FhirElementMapping> mappings = repository.findByMappingSchemeId(mappingSchemeId);

        Map<UUID, List<FhirElementMapping>> grouped = mappings.stream()
                .collect(Collectors.groupingBy(FhirElementMapping::getLocalItemId));

        List<FhirMappingConflictWarning> warnings = new ArrayList<>();
        for (Map.Entry<UUID, List<FhirElementMapping>> entry : grouped.entrySet()) {
            warnings.addAll(analyzeMappings(entry.getKey(), entry.getValue()));
        }
        return warnings;
    }

    // ── Internal analysis ─────────────────────────────────────────────────

    /**
     * Analyses a list of mappings for a single local item.
     * A conflict exists if there are multiple distinct fhirPaths.
     */
    List<FhirMappingConflictWarning> analyzeMappings(
            UUID localItemId, List<FhirElementMapping> mappings) {

        if (mappings == null || mappings.size() <= 1) {
            return Collections.emptyList();
        }

        // Group by distinct fhirPath
        Map<String, List<FhirElementMapping>> byPath = mappings.stream()
                .collect(Collectors.groupingBy(FhirElementMapping::getFhirPath));

        if (byPath.size() <= 1) {
            return Collections.emptyList();
        }

        List<String> paths = new ArrayList<>(byPath.keySet());
        return List.of(FhirMappingConflictWarning.of(localItemId, paths));
    }
}
