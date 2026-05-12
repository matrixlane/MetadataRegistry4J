package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.dataspecification.MeasureClass;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.concept.ConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.concept.ConceptResponse;
import io.github.matrixlane.metadataregistry.web.repository.MeasureClassRepository;
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
public class MeasureClassService {

    private final MeasureClassRepository repository;
    private final ConceptContentApplier conceptContentApplier;
    private final ItemAdministrationService itemAdministrationService;

    @Transactional(readOnly = true)
    public Page<ConceptResponse> list(String keyword, Pageable pageable) {
        Specification<MeasureClass> spec = byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ConceptResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ConceptResponse create(ConceptRequest request) {
        MeasureClass entity = new MeasureClass();
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        return toResponse(repository.save(entity));
    }

    public ConceptResponse update(UUID id, ConceptRequest request) {
        MeasureClass entity = loadOrThrow(id);
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("MeasureClass", id);
        }
        repository.deleteById(id);
    }

    private MeasureClass loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MeasureClass", id));
    }

    static <T extends Item> Specification<T> byKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return Specification.where(null);
        String like = "%" + keyword.toLowerCase() + "%";
        return (root, q, cb) -> {
            if (q != null) q.distinct(true);
            var d = root.join("designations", jakarta.persistence.criteria.JoinType.LEFT);
            return cb.like(cb.lower(d.get("sign").get("value")), like);
        };
    }

    private ConceptResponse toResponse(MeasureClass entity) {
        return new ConceptResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                entity.getSourceUri(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }
}
