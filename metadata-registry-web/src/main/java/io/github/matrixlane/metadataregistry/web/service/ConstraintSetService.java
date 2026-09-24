package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.ConstraintSet;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.constraintset.ConstraintSetRequest;
import io.github.matrixlane.metadataregistry.web.dto.constraintset.ConstraintSetResponse;
import io.github.matrixlane.metadataregistry.web.repository.ConstraintSetRepository;
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
public class ConstraintSetService {

    private final ConstraintSetRepository repository;
    private final ItemAdministrationService itemAdministrationService;

    @Transactional(readOnly = true)
    public Page<ConstraintSetResponse> list(String keyword, Pageable pageable) {
        Specification<ConstraintSet> spec = ObjectClassService.byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ConstraintSetResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ConstraintSetResponse create(ConstraintSetRequest request) {
        ConstraintSet entity = new ConstraintSet();
        applyRequest(entity, request);
        ConstraintSet saved = repository.save(entity);
        return toResponse(saved);
    }

    public ConstraintSetResponse update(UUID id, ConstraintSetRequest request) {
        ConstraintSet entity = loadOrThrow(id);
        applyRequest(entity, request);
        ConstraintSet saved = repository.save(entity);
        return toResponse(saved);
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("ConstraintSet", id);
        }
        repository.deleteById(id);
    }

    private ConstraintSet loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConstraintSet", id));
    }

    private void applyRequest(ConstraintSet entity, ConstraintSetRequest request) {
        entity.setItemShallBeDesignatedIndicator(
                request.itemShallBeDesignatedIndicator() != null
                        ? request.itemShallBeDesignatedIndicator() : true);
        entity.setItemShallBeDefinedIndicator(
                request.itemShallBeDefinedIndicator() != null
                        ? request.itemShallBeDefinedIndicator() : true);
        entity.setItemShallBeClassifiedIndicator(
                request.itemShallBeClassifiedIndicator() != null
                        ? request.itemShallBeClassifiedIndicator() : true);
        itemAdministrationService.applyDesignations(
                entity, request.designations(),
                request.registration() == null ? null : request.registration().namespaceId());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(entity, request.registration());
        }
    }

    private ConstraintSetResponse toResponse(ConstraintSet entity) {
        return new ConstraintSetResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                entity.getItemShallBeDesignatedIndicator(),
                entity.getItemShallBeDefinedIndicator(),
                entity.getItemShallBeClassifiedIndicator(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }
}
