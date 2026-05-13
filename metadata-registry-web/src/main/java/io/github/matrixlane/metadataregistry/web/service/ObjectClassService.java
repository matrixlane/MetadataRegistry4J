package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.concept.ConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.concept.ConceptResponse;
import io.github.matrixlane.metadataregistry.web.repository.ObjectClassRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ObjectClassService {

    private final ObjectClassRepository repository;
    private final ConceptContentApplier conceptContentApplier;
    private final ItemAdministrationService itemAdministrationService;
    private final VersionHistoryService versionHistoryService;

    @Transactional(readOnly = true)
    public Page<ConceptResponse> list(String keyword, Pageable pageable) {
        Specification<ObjectClass> spec = byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ConceptResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ConceptResponse create(ConceptRequest request) {
        ObjectClass entity = new ObjectClass();
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        ObjectClass saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "CREATE");
        return toResponse(saved);
    }

    public ConceptResponse update(UUID id, ConceptRequest request) {
        ObjectClass entity = loadOrThrow(id);
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        ObjectClass saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "UPDATE");
        return toResponse(saved);
    }

    public void delete(UUID id) {
        ObjectClass entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ObjectClass", id));
        versionHistoryService.recordSnapshot(entity, "DELETE");
        repository.deleteById(id);
    }

    private ObjectClass loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ObjectClass", id));
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

    private ConceptResponse toResponse(ObjectClass entity) {
        return new ConceptResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                entity.getSourceUri(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }
}
