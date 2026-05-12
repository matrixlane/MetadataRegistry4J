package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.concept.ConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.concept.ConceptResponse;
import io.github.matrixlane.metadataregistry.web.repository.UnitOfMeasureRepository;
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
public class UnitOfMeasureService {

    private final UnitOfMeasureRepository repository;
    private final ConceptContentApplier conceptContentApplier;
    private final ItemAdministrationService itemAdministrationService;

    @Transactional(readOnly = true)
    public Page<ConceptResponse> list(String keyword, Pageable pageable) {
        Specification<UnitOfMeasure> spec = ObjectClassService.byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ConceptResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ConceptResponse create(ConceptRequest request) {
        UnitOfMeasure entity = new UnitOfMeasure();
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        return toResponse(repository.save(entity));
    }

    public ConceptResponse update(UUID id, ConceptRequest request) {
        UnitOfMeasure entity = loadOrThrow(id);
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("UnitOfMeasure", id);
        }
        repository.deleteById(id);
    }

    private UnitOfMeasure loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", id));
    }

    private ConceptResponse toResponse(UnitOfMeasure entity) {
        return new ConceptResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                entity.getSourceUri(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }
}
