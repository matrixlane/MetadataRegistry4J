package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.dataspecification.Dimensionality;
import io.github.matrixlane.metadataregistry.dataspecification.MeasureClass;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.dimensionality.DimensionalityRef;
import io.github.matrixlane.metadataregistry.web.dto.measureclass.MeasureClassRequest;
import io.github.matrixlane.metadataregistry.web.dto.measureclass.MeasureClassResponse;
import io.github.matrixlane.metadataregistry.web.repository.DimensionalityRepository;
import io.github.matrixlane.metadataregistry.web.repository.MeasureClassRepository;
import io.github.matrixlane.metadataregistry.web.repository.UnitOfMeasureRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MeasureClass(Concept 子类)管理服务。
 * dimensionality_measure_class：计量类侧 0..*，关联按 id 整体替换，
 * null/空集清空；删除时被 unit_of_measure_class 引用则拒绝。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MeasureClassService {

    private final MeasureClassRepository repository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final DimensionalityRepository dimensionalityRepository;
    private final ConceptContentApplier conceptContentApplier;
    private final ItemAdministrationService itemAdministrationService;
    private final VersionHistoryService versionHistoryService;

    @Transactional(readOnly = true)
    public Page<MeasureClassResponse> list(String keyword, Pageable pageable) {
        Specification<MeasureClass> spec = byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public MeasureClassResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public MeasureClassResponse create(MeasureClassRequest request) {
        MeasureClass entity = new MeasureClass();
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        entity = repository.save(entity);
        if (request.dimensionalityIds() != null && !request.dimensionalityIds().isEmpty()) {
            applyDimensionalities(entity, request.dimensionalityIds());
        }
        versionHistoryService.recordSnapshot(entity, "CREATE", toResponse(entity));
        return toResponse(entity);
    }

    public MeasureClassResponse update(UUID id, MeasureClassRequest request) {
        MeasureClass entity = loadOrThrow(id);
        versionHistoryService.captureBaselineIfEnteringDraft(entity, toResponse(entity));
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        applyDimensionalities(entity, request.dimensionalityIds());
        MeasureClass saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "UPDATE", toResponse(saved));
        return toResponse(saved);
    }

    public void delete(UUID id) {
        MeasureClass entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MeasureClass", id));
        long references = unitOfMeasureRepository.countByMeasureClasses_Id(id);
        if (references > 0) {
            throw new BusinessException(ApiCodes.CONFLICT,
                    "计量类正被 %d 个计量单位引用，无法删除".formatted(references));
        }
        versionHistoryService.recordSnapshot(entity, "DELETE", toResponse(entity));
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

    /** dimensionalityIds 整体替换：计量类侧 0..*，null/空集清空；任一 id 不存在即拒绝。 */
    private void applyDimensionalities(MeasureClass entity, List<UUID> dimensionalityIds) {
        if (dimensionalityIds == null || dimensionalityIds.isEmpty()) {
            List<Dimensionality> current = dimensionalityRepository.findByMeasureClasses_Id(entity.getId());
            for (Dimensionality d : current) {
                d.getMeasureClasses().remove(entity);
            }
            entity.setDimensionalities(new ArrayList<>());
            dimensionalityRepository.saveAll(current);
            return;
        }

        List<Dimensionality> target = dimensionalityRepository.findAllById(dimensionalityIds);
        if (target.size() != dimensionalityIds.size()) {
            List<UUID> foundIds = target.stream().map(Dimensionality::getId).toList();
            UUID missing = dimensionalityIds.stream()
                    .filter(candidate -> !foundIds.contains(candidate))
                    .findFirst()
                    .orElseThrow();
            throw new ResourceNotFoundException("Dimensionality", missing);
        }

        List<Dimensionality> current = dimensionalityRepository.findByMeasureClasses_Id(entity.getId());
        List<Dimensionality> toRemove = current.stream()
                .filter(d -> target.stream().noneMatch(t -> t.getId().equals(d.getId())))
                .toList();
        List<Dimensionality> toAdd = target.stream()
                .filter(t -> current.stream().noneMatch(d -> d.getId().equals(t.getId())))
                .toList();

        for (Dimensionality d : toRemove) {
            d.getMeasureClasses().remove(entity);
        }
        for (Dimensionality d : toAdd) {
            d.getMeasureClasses().add(entity);
        }
        entity.setDimensionalities(new ArrayList<>(target));

        List<Dimensionality> toSave = new ArrayList<>(toRemove);
        toSave.addAll(toAdd);
        dimensionalityRepository.saveAll(toSave);
    }

    private MeasureClassResponse toResponse(MeasureClass entity) {
        List<DimensionalityRef> dimensionalities = entity.getDimensionalities().stream()
                .map(d -> new DimensionalityRef(d.getId(), ItemNamingHelper.preferredDesignation(d)))
                .toList();
        return new MeasureClassResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                entity.getSourceUri(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity),
                dimensionalities);
    }
}
