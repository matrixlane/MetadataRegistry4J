package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.MeasureClass;
import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.dimensionality.MeasureClassRef;
import io.github.matrixlane.metadataregistry.web.dto.unitofmeasure.UnitOfMeasureRequest;
import io.github.matrixlane.metadataregistry.web.dto.unitofmeasure.UnitOfMeasureResponse;
import io.github.matrixlane.metadataregistry.web.repository.MeasureClassRepository;
import io.github.matrixlane.metadataregistry.web.repository.UnitOfMeasureRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * UnitOfMeasure(Concept 子类)管理服务。
 * unit_of_measure_measure_class：UoM 侧 1..*，关联按 id 整体替换，
 * null/空按业务校验拒绝(422)。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UnitOfMeasureService {

    private final UnitOfMeasureRepository repository;
    private final MeasureClassRepository measureClassRepository;
    private final ConceptContentApplier conceptContentApplier;
    private final ItemAdministrationService itemAdministrationService;
    private final VersionHistoryService versionHistoryService;

    @Transactional(readOnly = true)
    public Page<UnitOfMeasureResponse> list(String keyword, Pageable pageable) {
        Specification<UnitOfMeasure> spec = ObjectClassService.byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UnitOfMeasureResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public UnitOfMeasureResponse create(UnitOfMeasureRequest request) {
        UnitOfMeasure entity = new UnitOfMeasure();
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        applyMeasureClasses(entity, request.measureClassIds());
        UnitOfMeasure saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "CREATE", toResponse(saved));
        return toResponse(saved);
    }

    public UnitOfMeasureResponse update(UUID id, UnitOfMeasureRequest request) {
        UnitOfMeasure entity = loadOrThrow(id);
        versionHistoryService.captureBaselineIfEnteringDraft(entity, toResponse(entity));
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        applyMeasureClasses(entity, request.measureClassIds());
        UnitOfMeasure saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "UPDATE", toResponse(saved));
        return toResponse(saved);
    }

    public void delete(UUID id) {
        UnitOfMeasure entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", id));
        versionHistoryService.recordSnapshot(entity, "DELETE", toResponse(entity));
        repository.deleteById(id);
    }

    private UnitOfMeasure loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", id));
    }

    /** measureClassIds 整体替换：UoM 侧 1..*，null/空拒绝；任一 id 不存在即拒绝。 */
    private void applyMeasureClasses(UnitOfMeasure entity, List<UUID> measureClassIds) {
        if (measureClassIds == null || measureClassIds.isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "计量单位必须至少归属一个计量类");
        }
        List<MeasureClass> found = measureClassRepository.findAllById(measureClassIds);
        if (found.size() != measureClassIds.size()) {
            List<UUID> foundIds = found.stream().map(MeasureClass::getId).toList();
            UUID missing = measureClassIds.stream()
                    .filter(candidate -> !foundIds.contains(candidate))
                    .findFirst()
                    .orElseThrow();
            throw new ResourceNotFoundException("MeasureClass", missing);
        }
        entity.setMeasureClasses(found);
    }

    private UnitOfMeasureResponse toResponse(UnitOfMeasure entity) {
        List<MeasureClassRef> measureClasses = entity.getMeasureClasses().stream()
                .map(mc -> new MeasureClassRef(mc.getId(), ItemNamingHelper.preferredDesignation(mc)))
                .toList();
        return new UnitOfMeasureResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                entity.getSourceUri(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity),
                measureClasses);
    }
}
