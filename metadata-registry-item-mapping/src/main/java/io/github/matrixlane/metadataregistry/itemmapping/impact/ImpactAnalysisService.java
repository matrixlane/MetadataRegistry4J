package io.github.matrixlane.metadataregistry.itemmapping.impact;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for analyzing the impact of mapping changes.
 *
 * <p>Performs BFS traversal up to 3 levels to identify cascading effects
 * when a metadata item or mapping relationship changes.
 *
 * <p>V1 scope: direct associated items + cascade mapping chain (max 3 levels).
 * Does NOT validate data instance consistency.
 */
@Service
@Transactional(readOnly = true)
public class ImpactAnalysisService {

    private static final int MAX_DEPTH = 3;
    private static final int MAX_NODES = 500;

    private final ItemMappingRepository mappingRepository;

    public ImpactAnalysisService(ItemMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    /**
     * Analyzes the impact of a change to a metadata item.
     *
     * <p>Identifies all mappings where the item appears as subject or object,
     * then follows the mapping graph up to 3 levels deep.
     *
     * @param itemId     the UUID of the changed item
     * @param changeType description of the change (e.g. "UPDATED", "DELETED")
     * @return the impact analysis result
     */
    public ImpactAnalysisResult analyzeItemChange(UUID itemId, String changeType) {
        List<ImpactedItem> impacted = new ArrayList<>();
        Set<UUID> visitedItems = new HashSet<>();
        Set<UUID> visitedMappings = new HashSet<>();
        boolean truncated = false;

        // BFS queue: (itemId, depth)
        Deque<BfsNode> queue = new ArrayDeque<>();
        queue.add(new BfsNode(itemId, 0, null));
        visitedItems.add(itemId);

        while (!queue.isEmpty()) {
            BfsNode current = queue.poll();

            if (current.depth >= MAX_DEPTH) {
                continue;
            }

            List<ItemMapping> mappings = mappingRepository.findByItemId(current.itemId);

            for (ItemMapping mapping : mappings) {
                if (!visitedMappings.add(mapping.getId())) {
                    continue;
                }

                UUID subjectId = mapping.getSubject().getItemId();
                UUID objectId = mapping.getObject().getItemId();

                // Skip external-only mappings (no internal item to track)
                if (subjectId == null || objectId == null) {
                    continue;
                }

                // Determine the neighbor and impact direction
                UUID neighborId;
                ImpactDirection direction;
                if (current.itemId.equals(subjectId)) {
                    neighborId = objectId;
                    direction = ImpactDirection.TARGET_AFFECTED;
                } else {
                    neighborId = subjectId;
                    direction = ImpactDirection.SOURCE_AFFECTED;
                }

                // For equivalent mappings, direction is both ways conceptually
                if (mapping.getMappingType() == MappingType.EQUIVALENT) {
                    direction = ImpactDirection.BOTH;
                }

                int levelDepth = current.depth + 1;
                ImpactLevel level = ImpactLevel.fromDepth(levelDepth);

                // Only add to result if neighbor is not the original changed item
                // (we don't report the changed item as impacted by itself)
                if (!neighborId.equals(itemId)) {
                    impacted.add(new ImpactedItem(
                            neighborId,
                            level,
                            direction,
                            mapping.getId(),
                            mapping.getMappingType(),
                            generateSuggestion(changeType, mapping.getMappingType(), level)
                    ));
                }

                // Continue BFS if not visited
                if (!visitedItems.contains(neighborId)) {
                    if (visitedItems.size() >= MAX_NODES) {
                        truncated = true;
                        continue;
                    }
                    visitedItems.add(neighborId);
                    queue.add(new BfsNode(neighborId, levelDepth, mapping.getId()));
                }
            }
        }

        // Count unique impacted items (excluding the changed item itself)
        long uniqueImpacted = impacted.stream()
                .map(ImpactedItem::itemId)
                .distinct()
                .count();

        return new ImpactAnalysisResult(
                itemId,
                changeType,
                impacted,
                (int) uniqueImpacted,
                truncated
        );
    }

    /**
     * Analyzes the impact of a specific mapping being modified or deleted.
     *
     * <p>Traces downstream effects starting from both subject and object of the mapping.
     *
     * @param mappingId  the UUID of the changed mapping
     * @param changeType description of the change (e.g. "MODIFIED", "DELETED")
     * @return the impact analysis result
     */
    public ImpactAnalysisResult analyzeMappingChange(UUID mappingId, String changeType) {
        ItemMapping mapping = mappingRepository.findById(mappingId).orElse(null);
        if (mapping == null) {
            return new ImpactAnalysisResult(null, changeType, List.of(), 0, false);
        }

        UUID subjectId = mapping.getSubject().getItemId();
        UUID objectId = mapping.getObject().getItemId();

        List<ImpactedItem> impacted = new ArrayList<>();
        Set<UUID> visitedItems = new HashSet<>();
        Set<UUID> visitedMappings = new HashSet<>();
        boolean truncated = false;

        // Add both endpoints as directly impacted through the changed mapping
        if (subjectId != null && objectId != null) {
            ImpactDirection directDirection = mapping.getMappingType() == MappingType.EQUIVALENT
                    ? ImpactDirection.BOTH
                    : ImpactDirection.TARGET_AFFECTED;
            impacted.add(new ImpactedItem(
                    objectId,
                    ImpactLevel.DIRECT,
                    directDirection,
                    mappingId,
                    mapping.getMappingType(),
                    generateSuggestion(changeType, mapping.getMappingType(), ImpactLevel.DIRECT)
            ));
            visitedItems.add(subjectId);
            visitedItems.add(objectId);
        }

        // BFS from both endpoints excluding the changed mapping.
        // Start depth = 1 so that neighbors reached via other mappings are INDIRECT (depth 2).
        if (subjectId != null) {
            var result = traverseFrom(subjectId, mappingId, visitedItems, visitedMappings, 1);
            impacted.addAll(result.items());
            truncated |= result.truncated();
        }

        if (objectId != null && !objectId.equals(subjectId)) {
            var result = traverseFrom(objectId, mappingId, visitedItems, visitedMappings, 1);
            impacted.addAll(result.items());
            truncated |= result.truncated();
        }

        // Remove duplicates (same item might appear from both directions)
        var uniqueImpacted = impacted.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ImpactedItem::itemId,
                        i -> i,
                        (a, b) -> a
                ))
                .values()
                .stream()
                .toList();

        long uniqueCount = uniqueImpacted.stream()
                .map(ImpactedItem::itemId)
                .distinct()
                .count();

        return new ImpactAnalysisResult(
                mappingId,
                changeType,
                uniqueImpacted,
                (int) uniqueCount,
                truncated
        );
    }

    private TraversalResult traverseFrom(UUID startItemId, UUID excludeMappingId,
                                          Set<UUID> globalVisitedItems,
                                          Set<UUID> globalVisitedMappings,
                                          int startDepth) {
        List<ImpactedItem> impacted = new ArrayList<>();
        boolean truncated = false;

        Deque<BfsNode> queue = new ArrayDeque<>();
        queue.add(new BfsNode(startItemId, startDepth, null));
        globalVisitedItems.add(startItemId);

        while (!queue.isEmpty()) {
            BfsNode current = queue.poll();

            if (current.depth >= MAX_DEPTH) {
                continue;
            }

            List<ItemMapping> mappings = mappingRepository.findByItemId(current.itemId);

            for (ItemMapping mapping : mappings) {
                // Skip the mapping that triggered this analysis
                if (mapping.getId().equals(excludeMappingId)) {
                    continue;
                }

                if (!globalVisitedMappings.add(mapping.getId())) {
                    continue;
                }

                UUID subjectId = mapping.getSubject().getItemId();
                UUID objectId = mapping.getObject().getItemId();

                if (subjectId == null || objectId == null) {
                    continue;
                }

                UUID neighborId = current.itemId.equals(subjectId) ? objectId : subjectId;

                ImpactDirection direction = current.itemId.equals(subjectId)
                        ? ImpactDirection.TARGET_AFFECTED
                        : ImpactDirection.SOURCE_AFFECTED;

                if (mapping.getMappingType() == MappingType.EQUIVALENT) {
                    direction = ImpactDirection.BOTH;
                }

                int levelDepth = current.depth + 1;
                ImpactLevel level = ImpactLevel.fromDepth(levelDepth);

                if (!neighborId.equals(startItemId)) {
                    impacted.add(new ImpactedItem(
                            neighborId,
                            level,
                            direction,
                            mapping.getId(),
                            mapping.getMappingType(),
                            generateSuggestion("MAPPING_CHANGED", mapping.getMappingType(), level)
                    ));
                }

                if (!globalVisitedItems.contains(neighborId)) {
                    if (globalVisitedItems.size() >= MAX_NODES) {
                        truncated = true;
                        continue;
                    }
                    globalVisitedItems.add(neighborId);
                    queue.add(new BfsNode(neighborId, levelDepth, mapping.getId()));
                }
            }
        }

        return new TraversalResult(impacted, truncated);
    }

    private String generateSuggestion(String changeType, MappingType mappingType, ImpactLevel level) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(level.name()).append("] ");

        switch (changeType) {
            case "DELETED" -> sb.append("Source mapping was deleted. ");
            case "UPDATED" -> sb.append("Source metadata item was updated. ");
            case "MODIFIED" -> sb.append("Mapping relationship was modified. ");
            default -> sb.append("Related mapping changed. ");
        }

        switch (mappingType) {
            case EQUIVALENT -> sb.append("Verify equivalence semantics remain valid.");
            case DERIVED -> sb.append("Review derivation rule for correctness.");
            case AGGREGATED -> sb.append("Check component aggregation integrity.");
            case SUBDIVIDED -> sb.append("Validate subdivision relationships.");
        }

        return sb.toString();
    }

    private record BfsNode(UUID itemId, int depth, UUID viaMappingId) {
    }

    private record TraversalResult(List<ImpactedItem> items, boolean truncated) {
    }
}
