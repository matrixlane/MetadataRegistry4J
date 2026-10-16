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
        var page0 = ItemListSorts.findAll(repository, spec, pageable);
        var preload0 = itemAdministrationService.preloadItemMeta(page0.getContent());
        return page0.map(entity -> toResponse(entity, preload0));
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
        ConstraintSet entity = loadOrThrow(id);
        // Recorded 及之后的注册状态已纳入版本控制，只能走 Retired，不允许直接删除
        itemAdministrationService.assertDeletable(entity);
        // 解除提交记录连接；关联条目删光后空的 SubmissionRecord 一并删除
        itemAdministrationService.removeFromSubmissionRecords(entity);
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


        return toResponse(entity, null);


    }



    private ConstraintSetResponse toResponse(ConstraintSet entity, ItemAdministrationService.ItemMetaPreload preload) {
        return new ConstraintSetResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                entity.getItemShallBeDesignatedIndicator(),
                entity.getItemShallBeDefinedIndicator(),
                entity.getItemShallBeClassifiedIndicator(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity, preload));
    }
}
