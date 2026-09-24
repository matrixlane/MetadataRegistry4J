package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.Dimensionality;
import io.github.matrixlane.metadataregistry.dataspecification.MeasureClass;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.dimensionality.DimensionalityRequest;
import io.github.matrixlane.metadataregistry.web.dto.dimensionality.DimensionalityResponse;
import io.github.matrixlane.metadataregistry.web.dto.dimensionality.MeasureClassRef;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;
import io.github.matrixlane.metadataregistry.web.repository.DimensionalityRepository;
import io.github.matrixlane.metadataregistry.web.repository.MeasureClassRepository;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.repository.SubmissionRecordRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Dimensionality(Concept 子类)管理服务。
 * coordinate_indicator 三态透传；dimensionality_measure_class
 * 关联按 id 整体替换;删除时被 conceptual_domain 引用则拒绝。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DimensionalityService {

    private final DimensionalityRepository repository;
    private final MeasureClassRepository measureClassRepository;
    private final ConceptualDomainRepository conceptualDomainRepository;
    private final ConceptContentApplier conceptContentApplier;
    private final ItemAdministrationService itemAdministrationService;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final DatabaseEnvironment databaseEnvironment;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public Page<DimensionalityResponse> list(String keyword, Pageable pageable) {
        return repository.findAll(MeasureClassService.byKeyword(keyword), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DimensionalityResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public DimensionalityResponse create(DimensionalityRequest request) {
        Dimensionality entity = new Dimensionality();
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        entity.setCoordinateIndicator(request.coordinateIndicator());
        applyMeasureClasses(entity, request.measureClassIds());
        Dimensionality saved = repository.save(entity);
        return toResponse(saved);
    }

    public DimensionalityResponse update(UUID id, DimensionalityRequest request) {
        Dimensionality entity = loadOrThrow(id);
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        entity.setCoordinateIndicator(request.coordinateIndicator());
        applyMeasureClasses(entity, request.measureClassIds());
        Dimensionality saved = repository.save(entity);
        return toResponse(saved);
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Dimensionality", id);
        }
        long references = conceptualDomainRepository.countByDimensionality_Id(id);
        if (references > 0) {
            throw new BusinessException(ApiCodes.CONFLICT,
                    "维度正被 %d 个概念域引用，无法删除".formatted(references));
        }
        // submission_record_registered_item.registered_item_id 外键指向 registered_item.id，
        // 而 registered_item.id 与 item.id（即 dimensionality.id）是 JOINED 继承下的不同 id：
        // 需先用 item_id 找到对应的 identified_item.id(=registered_item.id) 再清理。
        // 否则 JPA 级联删除会在 administered_item 处被外键 FKcs... 拦下报 409。
        //（参见 ContextTestDataInitializer.deleteAllContextsViaJdbc 同构写法）
        jdbcTemplate.update(
                "DELETE FROM " + databaseEnvironment.qualify("submission_record_registered_item")
                        + " WHERE registered_item_id IN ("
                        + "SELECT id FROM " + databaseEnvironment.qualify("identified_item")
                        + " WHERE item_id = ?)",
                id);
        repository.deleteById(id);
    }

    private Dimensionality loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dimensionality", id));
    }

    /** measureClassIds 整体替换:空集清空;任一 id 不存在即拒绝。 */
    private void applyMeasureClasses(Dimensionality entity, List<UUID> measureClassIds) {
        if (measureClassIds == null || measureClassIds.isEmpty()) {
            entity.getMeasureClasses().clear();
            return;
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

    private DimensionalityResponse toResponse(Dimensionality entity) {
        List<MeasureClassRef> measureClasses = entity.getMeasureClasses().stream()
                .map(mc -> new MeasureClassRef(mc.getId(), ItemNamingHelper.preferredDesignation(mc)))
                .toList();
        return new DimensionalityResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                entity.getSourceUri(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity),
                entity.getCoordinateIndicator(),
                measureClasses);
    }
}
