package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.MappingHierarchy;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.itemmapping.repository.MappingHierarchyRepository;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ItemMappingHierarchyRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ItemMappingRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ItemMappingResponse;
import io.github.matrixlane.metadataregistry.web.repository.IdentifiedItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 条目映射 CRUD 与层级维护。
 *
 * <p>条目解析走轻量存在性检查（{@link ItemRepository#existsItemById}），
 * 随后以 getReference 代理交给领域服务，避免加载 Item 联合子类对象图；
 * subject/object 的展示数据经标量投影查询组装。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ItemMappingCrudService {

    private static final Map<String, String> SORT_WHITELIST = Map.of(
            "id", "id",
            "degree", "degree",
            "description", "description");

    private final ItemMappingRepository mappingRepository;
    private final MappingHierarchyRepository hierarchyRepository;
    private final ItemRepository itemRepository;
    private final IdentifiedItemRepository identifiedItemRepository;
    private final ItemMappingService mappingService;
    private final ItemAdministrationService itemAdministrationService;

    // ---- 查询 ----

    @Transactional(readOnly = true)
    public Page<ItemMappingResponse> list(int page, int size, String sort,
                                          UUID subjectItemId, UUID objectItemId,
                                          MappingDegree degree) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(200, size)),
                parseSort(sort));
        Page<ItemMapping> result = query(pageable, subjectItemId, objectItemId, degree);
        List<UUID> ids = result.getContent().stream().map(ItemMapping::getId).toList();
        Map<UUID, RegistrationStatus> statuses = resolveRegistrationStatuses(ids);
        Map<UUID, List<Object[]>> subjectRows = rowsByMappingId(
                mappingRepository.findSubjectRowsByMappingIds(ids));
        Map<UUID, List<Object[]>> objectRows = rowsByMappingId(
                mappingRepository.findObjectRowsByMappingIds(ids));
        return result.map(m -> toResponse(m, statuses.get(m.getId()),
                subjectRows.getOrDefault(m.getId(), List.of()),
                objectRows.getOrDefault(m.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public ItemMappingResponse get(UUID id) {
        ItemMapping mapping = loadOrThrow(id);
        Map<UUID, RegistrationStatus> statuses = resolveRegistrationStatuses(List.of(id));
        Map<UUID, List<Object[]>> subjectRows = rowsByMappingId(
                mappingRepository.findSubjectRowsByMappingIds(List.of(id)));
        Map<UUID, List<Object[]>> objectRows = rowsByMappingId(
                mappingRepository.findObjectRowsByMappingIds(List.of(id)));
        return toResponse(mapping, statuses.get(id),
                subjectRows.getOrDefault(id, List.of()),
                objectRows.getOrDefault(id, List.of()));
    }

    private Page<ItemMapping> query(Pageable pageable, UUID subjectItemId,
                                    UUID objectItemId, MappingDegree degree) {
        boolean hasSubject = subjectItemId != null;
        boolean hasObject = objectItemId != null;
        boolean hasDegree = degree != null;

        if (hasSubject && hasObject && hasDegree) {
            return mappingRepository.findBySubjects_Item_IdAndObjects_Item_IdAndDegree(
                    subjectItemId, objectItemId, degree, pageable);
        }
        if (hasSubject && hasObject) {
            return mappingRepository.findBySubjects_Item_IdAndObjects_Item_Id(
                    subjectItemId, objectItemId, pageable);
        }
        if (hasSubject && hasDegree) {
            return mappingRepository.findBySubjects_Item_IdAndDegree(subjectItemId, degree, pageable);
        }
        if (hasObject && hasDegree) {
            return mappingRepository.findByObjects_Item_IdAndDegree(objectItemId, degree, pageable);
        }
        if (hasSubject) {
            return mappingRepository.findBySubjects_Item_Id(subjectItemId, pageable);
        }
        if (hasObject) {
            return mappingRepository.findByObjects_Item_Id(objectItemId, pageable);
        }
        if (hasDegree) {
            return mappingRepository.findByDegree(degree, pageable);
        }
        return mappingRepository.findAll(pageable);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.unsorted();
        }
        String[] parts = sort.split(",", 2);
        String field = SORT_WHITELIST.get(parts[0].trim());
        if (field == null) {
            return Sort.unsorted();
        }
        boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
        return Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, field);
    }

    // ---- 创建 / 更新 / 删除 ----

    public ItemMappingResponse create(ItemMappingRequest request) {
        ItemMapping saved = mappingService.createMapping(
                resolveBindings(request.subjects(), "subjects"),
                resolveBindings(request.objects(), "objects"),
                request.degree(),
                request.description(),
                request.transformationRule(),
                request.transformationRuleNotation());
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(saved, request.registration());
        }
        itemAdministrationService.recordSubmission(saved, request.registration());
        return get(saved.getId());
    }

    public ItemMappingResponse update(UUID id, ItemMappingRequest request) {
        ItemMapping mapping = loadOrThrow(id);
        ItemMapping saved = mappingService.updateMapping(
                mapping,
                request.degree(),
                resolveBindings(request.subjects(), "subjects"),
                resolveBindings(request.objects(), "objects"),
                request.description(),
                request.transformationRule(),
                request.transformationRuleNotation());
        return get(saved.getId());
    }

    public void delete(UUID id) {
        loadOrThrow(id);
        mappingService.deleteMapping(id);
    }

    private List<ItemMappingService.EndpointBinding> resolveBindings(
            List<ItemMappingRequest.Endpoint> endpoints, String field) {
        if (endpoints == null || endpoints.isEmpty()) {
            throw new BusinessException(field + " 至少需要一个绑定");
        }
        List<ItemMappingService.EndpointBinding> bindings = new ArrayList<>(endpoints.size());
        for (ItemMappingRequest.Endpoint endpoint : endpoints) {
            bindings.add(new ItemMappingService.EndpointBinding(
                    resolveItem(endpoint.itemId(), field + ".itemId"),
                    endpoint.variableName()));
        }
        return bindings;
    }

    private io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item
            resolveItem(UUID itemId, String field) {
        if (itemId == null) {
            throw new BusinessException(field + " 必须提供条目 UUID");
        }
        if (!itemRepository.existsItemById(itemId)) {
            throw new ResourceNotFoundException("Item", itemId);
        }
        return itemRepository.getReferenceById(itemId);
    }

    private ItemMapping loadOrThrow(UUID id) {
        return mappingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ItemMapping", id));
    }

    // ---- 层级 ----

    @Transactional(readOnly = true)
    public Map<String, Object> getHierarchy(UUID id) {
        loadOrThrow(id);
        UUID superset = hierarchyRepository.findBySubsetMappingId(id)
                .map(h -> h.getSupersetMapping().getId())
                .orElse(null);
        List<UUID> subsets = hierarchyRepository.findBySupersetMappingId(id).stream()
                .map(h -> h.getSubsetMapping().getId())
                .toList();
        Map<String, Object> body = new HashMap<>();
        body.put("supersetMappingId", superset);
        body.put("subsetMappingIds", subsets);
        return body;
    }

    /**
     * 设置/替换/清除某映射的上层（superset）关系；防自引用与循环。
     */
    public Map<String, Object> putHierarchy(UUID id, ItemMappingHierarchyRequest request) {
        loadOrThrow(id);
        UUID supersetId = request == null ? null : request.supersetMappingId();

        if (supersetId != null) {
            if (supersetId.equals(id)) {
                throw new BusinessException("映射不能将自身设为上层");
            }
            if (!mappingRepository.existsById(supersetId)) {
                throw new ResourceNotFoundException("ItemMapping", supersetId);
            }
            assertNoCycle(id, supersetId);
        }

        hierarchyRepository.deleteBySubsetMappingId(id);
        if (supersetId != null) {
            MappingHierarchy hierarchy = new MappingHierarchy(
                    mappingRepository.getReferenceById(supersetId),
                    mappingRepository.getReferenceById(id));
            hierarchyRepository.save(hierarchy);
        }
        return getHierarchy(id);
    }

    /**
     * 自 proposedSuperset 沿上层链向上走，若到达 mappingId 则成环。
     */
    private void assertNoCycle(UUID mappingId, UUID proposedSuperset) {
        UUID current = proposedSuperset;
        int guard = 0;
        while (current != null && guard++ < 10_000) {
            if (current.equals(mappingId)) {
                throw new BusinessException("设置该上层会形成循环层级");
            }
            UUID next = hierarchyRepository.findBySubsetMappingId(current)
                    .map(h -> h.getSupersetMapping().getId())
                    .orElse(null);
            current = next;
        }
    }

    // ---- 组装 ----

    private Map<UUID, RegistrationStatus> resolveRegistrationStatuses(List<UUID> mappingIds) {
        if (mappingIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, RegistrationStatus> statuses = new HashMap<>();
        for (Object[] row : identifiedItemRepository
                .findRegistrationStatusRowsByItemIds(mappingIds)) {
            statuses.put((UUID) row[0], (RegistrationStatus) row[1]);
        }
        return statuses;
    }

    private Map<UUID, List<Object[]>> rowsByMappingId(List<Object[]> rows) {
        Map<UUID, List<Object[]>> byId = new HashMap<>();
        for (Object[] row : rows) {
            byId.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add(row);
        }
        return byId;
    }

    private ItemMappingResponse toResponse(ItemMapping mapping, RegistrationStatus status,
                                           List<Object[]> subjectRows, List<Object[]> objectRows) {
        UUID id = mapping.getId();
        List<ItemMappingResponse.EndpointRef> subjects = subjectRows.stream()
                .map(row -> new ItemMappingResponse.EndpointRef((UUID) row[1], (String) row[2]))
                .toList();
        List<ItemMappingResponse.EndpointRef> objects = objectRows.stream()
                .map(row -> new ItemMappingResponse.EndpointRef((UUID) row[1], (String) row[2]))
                .toList();

        return new ItemMappingResponse(
                id,
                mapping.getDegree(),
                mapping.getDescription(),
                mapping.getTransformationRule(),
                mapping.getTransformationRuleNotation(),
                subjects,
                objects,
                status);
    }
}
