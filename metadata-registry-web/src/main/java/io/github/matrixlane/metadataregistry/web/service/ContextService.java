package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextRequest;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextResponse;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ContextService {

    private final ContextRepository repository;
    private final ItemAdministrationService itemAdministrationService;

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
}
