package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextUsageAssociation;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextUsageGroup;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextUsageItem;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextUsageResponse;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextRequest;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextResponse;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.DefinitionContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationDefinitionPairingRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ContextService {

    private final ContextRepository repository;
    private final ItemAdministrationService itemAdministrationService;
    private final DesignationContextRepository designationContextRepository;
    private final DefinitionContextRepository definitionContextRepository;
    private final DesignationDefinitionPairingRepository designationDefinitionPairingRepository;

    private static final int USAGE_LIMIT = 100;

    @Transactional(readOnly = true)
    public Page<ContextResponse> list(String keyword, Pageable pageable) {
        Specification<Context> spec = byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ContextResponse> options(String keyword) {
        Specification<Context> spec = byKeyword(keyword);
        return repository.findAll(spec).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContextResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ContextResponse create(ContextRequest request) {
        Context entity = new Context();
        itemAdministrationService.applyDesignations(
                entity, request.designations(),
                request.registration() == null ? null : request.registration().namespaceId());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(entity, request.registration());
        }
        return toResponse(repository.save(entity));
    }

    public ContextResponse update(UUID id, ContextRequest request) {
        Context entity = loadOrThrow(id);
        itemAdministrationService.applyDesignations(
                entity, request.designations(),
                request.registration() == null ? null : request.registration().namespaceId());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(entity, request.registration());
        }
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Context", id);
        }
        repository.deleteById(id);
    }

    private Context loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Context", id));
    }

    static <T extends Item> Specification<T> byKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return Specification.where(null);
        String like = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> {
            if (query != null) query.distinct(true);
            jakarta.persistence.criteria.Subquery<Long> sq = query.subquery(Long.class);
            var designation = sq.from(
                io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation.class);
            sq.select(cb.literal(1L));
            sq.where(cb.and(
                cb.equal(designation.get("item").get("id"), root.get("id")),
                cb.like(cb.lower(designation.get("sign").get("value")), like)));
            return cb.exists(sq);
        };
    }

    private ContextResponse toResponse(Context entity) {
        return new ContextResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }

    @Transactional(readOnly = true)
    public ContextUsageResponse findUsages(UUID contextId) {
        loadOrThrow(contextId);

        Pageable limit = PageRequest.of(0, USAGE_LIMIT);
        var designationContexts = designationContextRepository.findByContext_Id(contextId, limit).getContent();
        var definitionContexts = definitionContextRepository.findByContext_Id(contextId, limit).getContent();
        var pairings = designationDefinitionPairingRepository.findByContext_Id(contextId, limit).getContent();

        boolean truncated = designationContexts.size() == USAGE_LIMIT
                || definitionContexts.size() == USAGE_LIMIT
                || pairings.size() == USAGE_LIMIT;

        List<RawUsage> raw = new ArrayList<>();
        for (var dc : designationContexts) {
            raw.add(new RawUsage(
                    dc.getDesignation().getItem(),
                    new ContextUsageAssociation(
                            "DESIGNATION",
                            dc.getDesignation().getId(),
                            dc.getDesignation().getSign() == null ? null : dc.getDesignation().getSign().getValue(),
                            null,
                            null,
                            dc.getAcceptability() == null ? null : dc.getAcceptability().name())));
        }
        for (var dc : definitionContexts) {
            raw.add(new RawUsage(
                    dc.getDefinition().getItem(),
                    new ContextUsageAssociation(
                            "DEFINITION",
                            null,
                            null,
                            dc.getDefinition().getId(),
                            dc.getDefinition().getText() == null ? null : dc.getDefinition().getText().getValue(),
                            dc.getAcceptability() == null ? null : dc.getAcceptability().name())));
        }
        for (var p : pairings) {
            raw.add(new RawUsage(
                    p.getDesignation().getItem(),
                    new ContextUsageAssociation(
                            "PAIRING",
                            p.getDesignation().getId(),
                            p.getDesignation().getSign() == null ? null : p.getDesignation().getSign().getValue(),
                            p.getDefinition().getId(),
                            p.getDefinition().getText() == null ? null : p.getDefinition().getText().getValue(),
                            p.getAcceptability() == null ? null : p.getAcceptability().name())));
        }

        Map<UUID, ItemAssociations> byItem = new LinkedHashMap<>();
        for (var usage : raw) {
            byItem.computeIfAbsent(usage.item().getId(),
                            id -> new ItemAssociations(usage.item(), new ArrayList<>()))
                    .associations().add(usage.association());
        }

        List<ItemAssociations> sorted = byItem.values().stream()
                .sorted(Comparator.comparing((ItemAssociations g) -> g.item().getClass().getSimpleName())
                        .thenComparing(g -> ItemNamingHelper.preferredDesignation(g.item())))
                .toList();

        Map<String, List<ContextUsageItem>> byType = new LinkedHashMap<>();
        int total = 0;
        for (var group : sorted) {
            if (total >= USAGE_LIMIT) {
                truncated = true;
                break;
            }
            int remaining = USAGE_LIMIT - total;
            List<ContextUsageAssociation> taken = group.associations().size() <= remaining
                    ? group.associations()
                    : group.associations().subList(0, remaining);
            total += taken.size();
            if (taken.size() < group.associations().size()) {
                truncated = true;
            }
            String itemType = group.item().getClass().getSimpleName();
            byType.computeIfAbsent(itemType, k -> new ArrayList<>())
                    .add(new ContextUsageItem(
                            group.item().getId(),
                            ItemNamingHelper.preferredDesignation(group.item()),
                            taken));
        }

        List<ContextUsageGroup> groups = byType.entrySet().stream()
                .map(e -> new ContextUsageGroup(e.getKey(), itemTypeLabel(e.getKey()), e.getValue()))
                .toList();

        return new ContextUsageResponse(groups, truncated);
    }

    private static String itemTypeLabel(String itemType) {
        return switch (itemType) {
            case "Context" -> "上下文";
            case "DataElement" -> "数据元";
            case "Concept" -> "概念";
            case "ValueDomain" -> "值域";
            case "ObjectClass" -> "对象类";
            case "Property" -> "属性";
            case "DataElementConcept" -> "数据元概念";
            case "Namespace" -> "命名空间";
            case "NamingConvention" -> "命名约定";
            case "ReferenceDocument" -> "参考文档";
            default -> itemType;
        };
    }

    private record RawUsage(Item item, ContextUsageAssociation association) {
    }

    private record ItemAssociations(Item item, List<ContextUsageAssociation> associations) {
    }
}
