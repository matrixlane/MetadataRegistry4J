package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.MappingScheme;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.itemmapping.repository.MappingSchemeRepository;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingDiffEntry;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingDiffResponse;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingVersionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for computing differences between mapping scheme versions.
 *
 * <p>Per WI-011-2-3-2: Mapping Scheme Version Diff Viewer.
 * Compares two scheme versions and returns structured diff data showing
 * added, removed, and modified mappings.
 *
 * <h3>Diff Dimensions</h3>
 * <ul>
 *   <li><b>Added</b>: mappings present in compare version but not in base</li>
 *   <li><b>Removed</b>: mappings present in base but not in compare</li>
 *   <li><b>Modified</b>: same source+target pair but different attributes</li>
 * </ul>
 *
 * <h3>Performance</h3>
 * <p>Uses hash-based lookup (O(n)) for the comparison. Suitable for
 * schemes with up to several thousand mappings. For very large schemes,
 * consider streaming or pagination at the API level.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MappingDiffService {

    private final MappingSchemeRepository schemeRepository;
    private final ItemMappingRepository mappingRepository;

    /**
     * Computes the diff between two mapping scheme versions.
     *
     * @param baseId     the UUID of the base (older) scheme version
     * @param compareId  the UUID of the compare (newer) scheme version
     * @return structured diff response
     * @throws ResourceNotFoundException if either scheme does not exist
     */
    public MappingDiffResponse computeDiff(UUID baseId, UUID compareId, MappingType filterType) {
        MappingScheme base = schemeRepository.findById(baseId)
                .orElseThrow(() -> new ResourceNotFoundException("MappingScheme", baseId));
        MappingScheme compare = schemeRepository.findById(compareId)
                .orElseThrow(() -> new ResourceNotFoundException("MappingScheme", compareId));

        List<ItemMapping> baseMappings = mappingRepository.findByMappingSchemeId(baseId);
        List<ItemMapping> compareMappings = mappingRepository.findByMappingSchemeId(compareId);

        Map<String, ItemMapping> baseMap = indexByKey(baseMappings);
        Map<String, ItemMapping> compareMap = indexByKey(compareMappings);

        // Added: in compare but not in base
        List<MappingDiffEntry> added = new ArrayList<>();
        for (Map.Entry<String, ItemMapping> entry : compareMap.entrySet()) {
            if (!baseMap.containsKey(entry.getKey())) {
                ItemMapping m = entry.getValue();
                added.add(MappingDiffEntry.of(
                        formatSubject(m),
                        formatObject(m),
                        m.getMappingType(),
                        m.getDescription(),
                        m.getTransformationRule()
                ));
            }
        }

        // Removed: in base but not in compare
        List<MappingDiffEntry> removed = new ArrayList<>();
        for (Map.Entry<String, ItemMapping> entry : baseMap.entrySet()) {
            if (!compareMap.containsKey(entry.getKey())) {
                ItemMapping m = entry.getValue();
                removed.add(MappingDiffEntry.of(
                        formatSubject(m),
                        formatObject(m),
                        m.getMappingType(),
                        m.getDescription(),
                        m.getTransformationRule()
                ));
            }
        }

        // Modified: same key but different attributes
        List<MappingDiffEntry> modified = new ArrayList<>();
        for (String key : baseMap.keySet()) {
            ItemMapping baseMapping = baseMap.get(key);
            ItemMapping compareMapping = compareMap.get(key);
            if (compareMapping != null) {
                List<String> changedFields = new ArrayList<>();

                if (baseMapping.getMappingType() != compareMapping.getMappingType()) {
                    changedFields.add("mappingType");
                }
                if (!Objects.equals(baseMapping.getDescription(), compareMapping.getDescription())) {
                    changedFields.add("description");
                }
                if (!Objects.equals(baseMapping.getTransformationRule(), compareMapping.getTransformationRule())) {
                    changedFields.add("transformationRule");
                }

                if (!changedFields.isEmpty()) {
                    modified.add(MappingDiffEntry.ofModified(
                            formatSubject(baseMapping),
                            formatObject(baseMapping),
                            new MappingDiffEntry.MappingSnapshot(
                                    baseMapping.getMappingType(),
                                    baseMapping.getDescription(),
                                    baseMapping.getTransformationRule()
                            ),
                            new MappingDiffEntry.MappingSnapshot(
                                    compareMapping.getMappingType(),
                                    compareMapping.getDescription(),
                                    compareMapping.getTransformationRule()
                            ),
                            changedFields
                    ));
                }
            }
        }

        // Apply mapping type filter if specified
        if (filterType != null) {
            added = added.stream()
                    .filter(e -> e.mappingType() == filterType)
                    .toList();
            removed = removed.stream()
                    .filter(e -> e.mappingType() == filterType)
                    .toList();
            modified = modified.stream()
                    .filter(e -> {
                        // For modified entries, check either from or to mappingType
                        if (e.from() != null && e.from().mappingType() == filterType) return true;
                        if (e.to() != null && e.to().mappingType() == filterType) return true;
                        return false;
                    })
                    .toList();
        }

        int totalDiffCount = added.size() + removed.size() + modified.size();

        log.info("Computed diff between scheme {} (v{}) and {} (v{}): {} added, {} removed, {} modified",
                baseId, base.getVersion(), compareId, compare.getVersion(),
                added.size(), removed.size(), modified.size());

        return new MappingDiffResponse(
                baseId,
                base.getVersion(),
                compareId,
                compare.getVersion(),
                added,
                removed,
                modified,
                totalDiffCount,
                totalDiffCount == 0
        );
    }

    /**
     * Returns all versions in the same lineage as the given scheme.
     *
     * <p>Traverses both up (ancestors via parentId) and down (descendants
     * via findByParentId) to collect the full version history.
     *
     * @param schemeId the UUID of any scheme in the lineage
     * @return list of version infos, ordered from oldest to newest
     */
    public List<MappingVersionInfo> getVersionLineage(UUID schemeId) {
        MappingScheme start = schemeRepository.findById(schemeId)
                .orElseThrow(() -> new ResourceNotFoundException("MappingScheme", schemeId));

        Set<UUID> visited = new HashSet<>();
        List<MappingScheme> lineage = new ArrayList<>();

        // Find root ancestor
        MappingScheme current = start;
        while (current.getParentId() != null) {
            if (!visited.add(current.getId())) {
                break; // cycle protection
            }
            Optional<MappingScheme> parent = schemeRepository.findById(current.getParentId());
            if (parent.isEmpty()) {
                break;
            }
            current = parent.get();
        }

        // Now current is the root; collect all descendants via BFS
        Queue<MappingScheme> queue = new LinkedList<>();
        queue.add(current);
        visited.clear();

        while (!queue.isEmpty()) {
            MappingScheme scheme = queue.poll();
            if (!visited.add(scheme.getId())) {
                continue;
            }
            lineage.add(scheme);
            queue.addAll(schemeRepository.findByParentId(scheme.getId()));
        }

        // Sort by createdAt ascending (oldest first)
        lineage.sort(Comparator.comparing(MappingScheme::getCreatedAt));

        return lineage.stream()
                .map(s -> new MappingVersionInfo(
                        s.getId(),
                        s.getVersion(),
                        s.getStatus(),
                        s.getParentId(),
                        s.getCreatedAt()
                ))
                .toList();
    }

    // ---- Internal Helpers ----

    /**
     * Builds a unique key for a mapping based on its subject and object references.
     * Key format: "subject:{itemId}|object:{itemId}" or "subject:uri:{uri}|object:uri:{uri}"
     */
    private String buildKey(ItemMapping mapping) {
        String subjectKey = mapping.getSubject().getItemId() != null
                ? "item:" + mapping.getSubject().getItemId()
                : "uri:" + mapping.getSubject().getExternalUri();
        String objectKey = mapping.getObject().getItemId() != null
                ? "item:" + mapping.getObject().getItemId()
                : "uri:" + mapping.getObject().getExternalUri();
        return subjectKey + "|" + objectKey;
    }

    private Map<String, ItemMapping> indexByKey(List<ItemMapping> mappings) {
        return mappings.stream()
                .collect(Collectors.toMap(this::buildKey, m -> m, (a, b) -> a));
    }

    private String formatSubject(ItemMapping mapping) {
        return mapping.getSubject().getItemId() != null
                ? mapping.getSubject().getItemId().toString()
                : mapping.getSubject().getExternalUri();
    }

    private String formatObject(ItemMapping mapping) {
        return mapping.getObject().getItemId() != null
                ? mapping.getObject().getItemId().toString()
                : mapping.getObject().getExternalUri();
    }
}
