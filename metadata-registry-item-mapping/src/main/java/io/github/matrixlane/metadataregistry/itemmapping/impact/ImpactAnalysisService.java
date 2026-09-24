package io.github.matrixlane.metadataregistry.itemmapping.impact;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 映射变更影响分析服务。
 *
 * <p>条目或映射发生变更时沿映射图 BFS（最多 3 层 / 500 节点），识别级联影响。
 * 全程使用标量投影查询（mappingId / degree / 条目 id 集合），不加载
 * Item 联合子类对象图。
 *
 * <p>方向判定：对称映射度（same as、semantically equivalent）视为双向影响；
 * 其余按绑定角色判定——当前条目为主体、另一条目为客体 → TARGET_AFFECTED；
 * 当前条目为客体、另一条目为主体 → SOURCE_AFFECTED；同侧条目之间按双向
 * （BOTH）保守处理。多主体/多客体映射对每对条目分别给出影响项。
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
     * 一条映射的轻量上下文：度 + 主体条目集合 + 客体条目集合。
     */
    private record MappingContext(UUID mappingId, MappingDegree degree,
                                  Set<UUID> subjectIds, Set<UUID> objectIds) {}

    private MappingContext loadContext(UUID mappingId) {
        List<Object[]> degreeRows = mappingRepository.findDegreesByMappingIds(List.of(mappingId));
        if (degreeRows.isEmpty()) {
            return null;
        }
        MappingDegree degree = (MappingDegree) degreeRows.get(0)[1];
        Set<UUID> subjectIds = new LinkedHashSet<>();
        for (Object[] row : mappingRepository.findSubjectRowsByMappingIds(List.of(mappingId))) {
            subjectIds.add((UUID) row[1]);
        }
        Set<UUID> objectIds = new LinkedHashSet<>();
        for (Object[] row : mappingRepository.findObjectRowsByMappingIds(List.of(mappingId))) {
            objectIds.add((UUID) row[1]);
        }
        return new MappingContext(mappingId, degree, subjectIds, objectIds);
    }

    /**
     * 分析条目变更的影响范围。
     *
     * @param itemId     变更条目的 UUID
     * @param changeType 变更类型描述（如 "UPDATED"、"DELETED"）
     */
    public ImpactAnalysisResult analyzeItemChange(UUID itemId, String changeType) {
        List<ImpactedItem> impacted = new ArrayList<>();
        Set<UUID> visitedItems = new HashSet<>();
        Set<UUID> visitedMappings = new HashSet<>();
        boolean truncated = false;

        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(itemId);
        visitedItems.add(itemId);
        Map<UUID, Integer> depths = new java.util.HashMap<>();
        depths.put(itemId, 0);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            int currentDepth = depths.getOrDefault(current, 0);
            if (currentDepth >= MAX_DEPTH) {
                continue;
            }

            for (UUID mappingId : mappingRepository.findMappingIdsByItemId(current)) {
                if (!visitedMappings.add(mappingId)) {
                    continue;
                }
                MappingContext ctx = loadContext(mappingId);
                if (ctx == null) {
                    continue;
                }

                Set<UUID> involved = new LinkedHashSet<>();
                involved.addAll(ctx.subjectIds());
                involved.addAll(ctx.objectIds());

                for (UUID other : involved) {
                    if (other.equals(current)) {
                        continue;
                    }
                    ImpactDirection direction = direction(current, other, ctx);
                    int levelDepth = currentDepth + 1;
                    ImpactLevel level = ImpactLevel.fromDepth(levelDepth);
                    if (!other.equals(itemId)) {
                        impacted.add(new ImpactedItem(
                                other, level, direction, mappingId, ctx.degree(),
                                generateSuggestion(changeType, ctx.degree(), level)));
                    }
                    if (!visitedItems.contains(other)) {
                        if (visitedItems.size() >= MAX_NODES) {
                            truncated = true;
                            continue;
                        }
                        visitedItems.add(other);
                        depths.put(other, levelDepth);
                        queue.add(other);
                    }
                }
            }
        }

        long uniqueImpacted = impacted.stream().map(ImpactedItem::itemId).distinct().count();
        return new ImpactAnalysisResult(itemId, changeType, impacted, (int) uniqueImpacted, truncated);
    }

    /**
     * 分析单条映射变更的影响：全部客体条目直接受影响，再从每个涉及条目向外遍历。
     */
    public ImpactAnalysisResult analyzeMappingChange(UUID mappingId, String changeType) {
        MappingContext ctx = loadContext(mappingId);
        if (ctx == null) {
            return new ImpactAnalysisResult(null, changeType, List.of(), 0, false);
        }

        List<ImpactedItem> impacted = new ArrayList<>();
        Set<UUID> visitedItems = new HashSet<>();
        Set<UUID> visitedMappings = new HashSet<>();
        boolean truncated = false;

        Set<UUID> involved = new LinkedHashSet<>();
        involved.addAll(ctx.subjectIds());
        involved.addAll(ctx.objectIds());

        // 客体条目经本条映射直接受影响
        for (UUID objectId : ctx.objectIds()) {
            ImpactDirection directDirection = ctx.degree().isSymmetric()
                    ? ImpactDirection.BOTH
                    : ImpactDirection.TARGET_AFFECTED;
            impacted.add(new ImpactedItem(
                    objectId, ImpactLevel.DIRECT, directDirection, mappingId, ctx.degree(),
                    generateSuggestion(changeType, ctx.degree(), ImpactLevel.DIRECT)));
        }
        visitedItems.addAll(involved);

        // 从每个涉及条目向外遍历（排除触发本条映射）
        for (UUID itemId : involved) {
            var result = traverseFrom(itemId, mappingId, visitedItems, visitedMappings, 1);
            impacted.addAll(result.items());
            truncated |= result.truncated();
        }

        var uniqueImpacted = impacted.stream()
                .collect(Collectors.toMap(ImpactedItem::itemId, i -> i, (a, b) -> a))
                .values().stream().toList();
        long uniqueCount = uniqueImpacted.stream().map(ImpactedItem::itemId).distinct().count();

        return new ImpactAnalysisResult(mappingId, changeType, uniqueImpacted,
                (int) uniqueCount, truncated);
    }

    private TraversalResult traverseFrom(UUID startItemId, UUID excludeMappingId,
                                          Set<UUID> globalVisitedItems,
                                          Set<UUID> globalVisitedMappings,
                                          int startDepth) {
        List<ImpactedItem> impacted = new ArrayList<>();
        boolean truncated = false;

        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(startItemId);
        Map<UUID, Integer> depths = new java.util.HashMap<>();
        depths.put(startItemId, startDepth);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            int currentDepth = depths.getOrDefault(current, startDepth);
            if (currentDepth >= MAX_DEPTH) {
                continue;
            }

            for (UUID mappingId : mappingRepository.findMappingIdsByItemId(current)) {
                if (mappingId.equals(excludeMappingId)) {
                    continue;
                }
                if (!globalVisitedMappings.add(mappingId)) {
                    continue;
                }
                MappingContext ctx = loadContext(mappingId);
                if (ctx == null) {
                    continue;
                }

                Set<UUID> involved = new LinkedHashSet<>();
                involved.addAll(ctx.subjectIds());
                involved.addAll(ctx.objectIds());

                for (UUID other : involved) {
                    if (other.equals(current)) {
                        continue;
                    }
                    ImpactDirection direction = direction(current, other, ctx);
                    int levelDepth = currentDepth + 1;
                    ImpactLevel level = ImpactLevel.fromDepth(levelDepth);
                    if (!other.equals(startItemId)) {
                        impacted.add(new ImpactedItem(
                                other, level, direction, mappingId, ctx.degree(),
                                generateSuggestion("MAPPING_CHANGED", ctx.degree(), level)));
                    }
                    if (!globalVisitedItems.contains(other)) {
                        if (globalVisitedItems.size() >= MAX_NODES) {
                            truncated = true;
                            continue;
                        }
                        globalVisitedItems.add(other);
                        depths.put(other, levelDepth);
                        queue.add(other);
                    }
                }
            }
        }

        return new TraversalResult(impacted, truncated);
    }

    /**
     * 判定 current → other 的影响方向。
     */
    private ImpactDirection direction(UUID current, UUID other, MappingContext ctx) {
        if (ctx.degree().isSymmetric()) {
            return ImpactDirection.BOTH;
        }
        boolean currentSubject = ctx.subjectIds().contains(current);
        boolean currentObject = ctx.objectIds().contains(current);
        boolean otherSubject = ctx.subjectIds().contains(other);
        boolean otherObject = ctx.objectIds().contains(other);
        if (currentSubject && otherObject) {
            return ImpactDirection.TARGET_AFFECTED;
        }
        if (currentObject && otherSubject) {
            return ImpactDirection.SOURCE_AFFECTED;
        }
        return ImpactDirection.BOTH;
    }

    private String generateSuggestion(String changeType, MappingDegree degree, ImpactLevel level) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(level.name()).append("] ");

        switch (changeType) {
            case "DELETED" -> sb.append("Source mapping was deleted. ");
            case "UPDATED" -> sb.append("Source metadata item was updated. ");
            case "MODIFIED" -> sb.append("Mapping relationship was modified. ");
            default -> sb.append("Related mapping changed. ");
        }

        switch (degree) {
            case SAME_AS, SEMANTICALLY_EQUIVALENT ->
                    sb.append("Verify equivalence semantics remain valid.");
            case DERIVED_FROM, HAS_DERIVATION ->
                    sb.append("Review derivation rule for correctness.");
            case CONTAINS, CONTAINED_IN, BROADER_THAN, NARROWER_THAN ->
                    sb.append("Check containment/breadth relationships for consistency.");
            case CHILD_OF, PARENT_OF ->
                    sb.append("Validate hierarchical relationships.");
            case INSTANCE_OF, HAS_INSTANCE ->
                    sb.append("Validate instance relationships.");
            default ->
                    sb.append("Review the mapping description for impact.");
        }

        return sb.toString();
    }

    private record TraversalResult(List<ImpactedItem> items, boolean truncated) {}
}
