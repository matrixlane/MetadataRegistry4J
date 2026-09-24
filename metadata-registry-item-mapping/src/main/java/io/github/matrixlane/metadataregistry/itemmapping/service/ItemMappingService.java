package io.github.matrixlane.metadataregistry.itemmapping.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import io.github.matrixlane.metadataregistry.itemmapping.event.MappingChangedEvent;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.itemmapping.repository.MappingHierarchyRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 条目映射领域服务。
 *
 * <p>负责映射条目的创建、更新、删除与映射网络图查询。校验规则：
 * degree 必填（枚举层保证）、transformation_rule 存在时
 * transformation_rule_notation 必填（条件属性）、主体/客体绑定
 * 各至少一个且被引用条目非空（存在性由调用方在解析条目时校验）。
 *
 * <p>主体/客体为标准多对多关联类形态：一条映射可有 1..* 个主体与
 * 1..* 个客体，每个绑定（Subject_Mapping/Object_Mapping 行）各自携带
 * mapping_variable_name。标准未对映射图施加互斥/成环限制（层级关系由
 * {@link MappingHierarchyRepository} 单独建模）。
 */
@Service
@Transactional
public class ItemMappingService {

    /**
     * 一个端点绑定：条目 + 可选映射变量名（转换规则中的变量引用）。
     */
    public record EndpointBinding(Item item, String variableName) {}

    private final ItemMappingRepository mappingRepository;
    private final MappingHierarchyRepository hierarchyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ItemMappingService(ItemMappingRepository mappingRepository,
                              MappingHierarchyRepository hierarchyRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.mappingRepository = mappingRepository;
        this.hierarchyRepository = hierarchyRepository;
        this.eventPublisher = eventPublisher;
    }

    // ---- 创建 / 更新 ----

    /**
     * 创建一条映射（1..* 主体 × 1..* 客体，忠实标准形态）。
     *
     * @param subjectBindings 主体绑定列表（≥1，条目须已由调用方解析确认存在）
     * @param objectBindings  客体绑定列表（≥1）
     * @param degree                  映射度（必填）
     * @param description             描述（可空）
     * @param transformationRule      转换规则（可空）
     * @param transformationRuleNotation 转换规则记法（rule 存在时必填）
     * @return 持久化后的映射条目
     * @throws IllegalArgumentException 校验失败
     */
    public ItemMapping createMapping(List<EndpointBinding> subjectBindings,
                                     List<EndpointBinding> objectBindings,
                                     MappingDegree degree,
                                     String description, String transformationRule,
                                     String transformationRuleNotation) {
        validate(degree, transformationRule, transformationRuleNotation,
                subjectBindings, objectBindings);

        ItemMapping mapping = new ItemMapping();
        mapping.setDegree(degree);
        mapping.setDescription(description);
        mapping.setTransformationRule(transformationRule);
        mapping.setTransformationRuleNotation(transformationRuleNotation);

        for (EndpointBinding binding : subjectBindings) {
            mapping.getSubjects().add(
                    new ItemMappingSubject(mapping, binding.item(), binding.variableName()));
        }
        for (EndpointBinding binding : objectBindings) {
            mapping.getObjects().add(
                    new ItemMappingObject(mapping, binding.item(), binding.variableName()));
        }

        ItemMapping saved = mappingRepository.save(mapping);
        eventPublisher.publishEvent(new MappingChangedEvent(this, saved.getId(), "CREATED", saved));
        return saved;
    }

    /**
     * 更新映射的可变属性并重置主体/客体绑定集合。
     */
    public ItemMapping updateMapping(ItemMapping mapping, MappingDegree degree,
                                     List<EndpointBinding> subjectBindings,
                                     List<EndpointBinding> objectBindings,
                                     String description, String transformationRule,
                                     String transformationRuleNotation) {
        validate(degree, transformationRule, transformationRuleNotation,
                subjectBindings, objectBindings);

        mapping.setDegree(degree);
        mapping.setDescription(description);
        mapping.setTransformationRule(transformationRule);
        mapping.setTransformationRuleNotation(transformationRuleNotation);

        mapping.getSubjects().clear();
        for (EndpointBinding binding : subjectBindings) {
            mapping.getSubjects().add(
                    new ItemMappingSubject(mapping, binding.item(), binding.variableName()));
        }
        mapping.getObjects().clear();
        for (EndpointBinding binding : objectBindings) {
            mapping.getObjects().add(
                    new ItemMappingObject(mapping, binding.item(), binding.variableName()));
        }

        ItemMapping saved = mappingRepository.save(mapping);
        eventPublisher.publishEvent(new MappingChangedEvent(this, saved.getId(), "MODIFIED", saved));
        return saved;
    }

    private void validate(MappingDegree degree, String transformationRule,
                          String transformationRuleNotation,
                          List<EndpointBinding> subjectBindings,
                          List<EndpointBinding> objectBindings) {
        if (degree == null) {
            throw new IllegalArgumentException("degree must not be null");
        }
        if (transformationRule != null && !transformationRule.isBlank()
                && (transformationRuleNotation == null || transformationRuleNotation.isBlank())) {
            throw new IllegalArgumentException(
                    "transformationRuleNotation is required when transformationRule is present");
        }
        if (subjectBindings == null || subjectBindings.isEmpty()) {
            throw new IllegalArgumentException("At least one subject binding is required");
        }
        if (objectBindings == null || objectBindings.isEmpty()) {
            throw new IllegalArgumentException("At least one object binding is required");
        }
        for (EndpointBinding binding : subjectBindings) {
            if (binding == null || binding.item() == null) {
                throw new IllegalArgumentException("Subject binding item must not be null");
            }
        }
        for (EndpointBinding binding : objectBindings) {
            if (binding == null || binding.item() == null) {
                throw new IllegalArgumentException("Object binding item must not be null");
            }
        }
    }

    // ---- 查询 ----

    @Transactional(readOnly = true)
    public List<ItemMapping> getOutgoingMappings(UUID itemId) {
        return mappingRepository.findBySubjects_Item_Id(itemId, org.springframework.data.domain.Pageable.unpaged())
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<ItemMapping> getIncomingMappings(UUID itemId) {
        return mappingRepository.findByObjects_Item_Id(itemId, org.springframework.data.domain.Pageable.unpaged())
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<ItemMapping> getAllMappingsForItem(UUID itemId) {
        Set<ItemMapping> all = new LinkedHashSet<>(getOutgoingMappings(itemId));
        all.addAll(getIncomingMappings(itemId));
        return List.copyOf(all);
    }

    // ---- 删除 ----

    /**
     * 删除映射条目，并清理其层级关系（作为 superset 与 subset 两侧）。
     * 主体/客体绑定行经 cascade + orphanRemoval 级联删除。
     */
    public void deleteMapping(UUID mappingId) {
        ItemMapping mapping = mappingRepository.findById(mappingId).orElse(null);
        if (mapping == null) {
            throw new IllegalArgumentException("Mapping not found: " + mappingId);
        }
        hierarchyRepository.deleteBySupersetMappingId(mappingId);
        hierarchyRepository.deleteBySubsetMappingId(mappingId);
        mappingRepository.deleteById(mappingId);
        eventPublisher.publishEvent(new MappingChangedEvent(this, mappingId, "DELETED", mapping));
    }

    // ---- 映射网络图 ----

    public record NetworkGraphResult(
            Set<NetworkNode> nodes,
            List<NetworkEdge> edges,
            boolean truncated,
            int totalNodeCount
    ) {}

    public record NetworkNode(UUID itemId, int depth, boolean isCenter) {}

    public record NetworkEdge(UUID mappingId, UUID sourceId, UUID targetId,
                              MappingDegree degree, String description) {}

    /**
     * 以某条目为中心做 BFS（1-3 层，默认上限 500 节点），可按映射度过滤。
     * 全程使用标量投影查询，不加载 Item 实体。多主体/多客体映射在网络图中
     * 展开为 subject×object 的边。
     */
    @Transactional(readOnly = true)
    public NetworkGraphResult getNetworkGraph(UUID centerId, int maxDepth,
                                              List<MappingDegree> filterDegrees) {
        maxDepth = Math.max(1, Math.min(3, maxDepth));

        Set<UUID> visited = new LinkedHashSet<>();
        List<NetworkEdge> edges = new ArrayList<>();
        Set<UUID> visitedMappings = new HashSet<>();
        Map<UUID, Integer> nodeDepths = new LinkedHashMap<>();
        Deque<Map.Entry<UUID, Integer>> queue = new ArrayDeque<>();

        queue.add(Map.entry(centerId, 0));
        visited.add(centerId);
        nodeDepths.put(centerId, 0);
        boolean truncated = false;

        while (!queue.isEmpty()) {
            var entry = queue.poll();
            UUID currentId = entry.getKey();
            int currentDepth = entry.getValue();

            if (currentDepth >= maxDepth) {
                continue;
            }

            for (UUID mappingId : mappingRepository.findMappingIdsByItemId(currentId)) {
                if (!visitedMappings.add(mappingId)) {
                    continue;
                }

                List<Object[]> degreeRows = mappingRepository.findDegreesByMappingIds(List.of(mappingId));
                if (degreeRows.isEmpty()) {
                    continue;
                }
                MappingDegree degree = (MappingDegree) degreeRows.get(0)[1];
                if (filterDegrees != null && !filterDegrees.isEmpty()
                        && !filterDegrees.contains(degree)) {
                    continue;
                }

                List<Object[]> subjectRows = mappingRepository.findSubjectRowsByMappingIds(List.of(mappingId));
                List<Object[]> objectRows = mappingRepository.findObjectRowsByMappingIds(List.of(mappingId));

                String description = null;
                ItemMapping mapping = mappingRepository.findById(mappingId).orElse(null);
                if (mapping != null) {
                    description = mapping.getDescription();
                }

                for (Object[] subjectRow : subjectRows) {
                    UUID subjectId = (UUID) subjectRow[1];
                    for (Object[] objectRow : objectRows) {
                        UUID objectId = (UUID) objectRow[1];
                        edges.add(new NetworkEdge(mappingId, subjectId, objectId, degree, description));
                    }
                }

                for (Object[] row : subjectRows) {
                    truncated |= !maybeVisit((UUID) row[1], currentId, currentDepth, visited,
                            nodeDepths, queue, 500);
                }
                for (Object[] row : objectRows) {
                    truncated |= !maybeVisit((UUID) row[1], currentId, currentDepth, visited,
                            nodeDepths, queue, 500);
                }
            }
        }

        Set<NetworkNode> nodes = new LinkedHashSet<>();
        for (UUID itemId : visited) {
            nodes.add(new NetworkNode(itemId, nodeDepths.getOrDefault(itemId, 0),
                    itemId.equals(centerId)));
        }
        return new NetworkGraphResult(nodes, edges, truncated, visited.size());
    }

    private boolean maybeVisit(UUID neighborId, UUID currentId, int currentDepth,
                               Set<UUID> visited, Map<UUID, Integer> nodeDepths,
                               Deque<Map.Entry<UUID, Integer>> queue, int limit) {
        if (neighborId.equals(currentId) || visited.contains(neighborId)) {
            return true;
        }
        if (visited.size() >= limit) {
            return false;
        }
        visited.add(neighborId);
        nodeDepths.put(neighborId, currentDepth + 1);
        queue.add(Map.entry(neighborId, currentDepth + 1));
        return true;
    }
}
