package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Datetime_Period;
import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.EnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ValueMeaning;
import io.github.matrixlane.metadataregistry.dataspecification.ValueMeaningSet;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainRequest;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainResponse;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainType;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ValueMeaningEntry;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ValueMeaningResponse;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueMeaningRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueMeaningSetRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import io.github.matrixlane.metadataregistry.web.util.ValidPeriodOverlapChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ConceptualDomainService {

    private final ConceptualDomainRepository repository;
    private final ValueMeaningRepository valueMeaningRepository;
    private final ValueMeaningSetRepository valueMeaningSetRepository;
    private final ConceptContentApplier conceptContentApplier;
    private final ItemAdministrationService itemAdministrationService;
    private final VersionHistoryService versionHistoryService;

    @Transactional(readOnly = true)
    public Page<ConceptualDomainResponse> list(String keyword, String type, Pageable pageable) {
        Specification<ConceptualDomain> spec = ObjectClassService.byKeyword(keyword);
        Specification<ConceptualDomain> typeSpec = byType(type);
        if (typeSpec != null) {
            spec = (spec == null) ? typeSpec : spec.and(typeSpec);
        }
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ConceptualDomainResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ConceptualDomainResponse create(ConceptualDomainRequest request) {
        ConceptualDomainType targetType = request.type() == null ? ConceptualDomainType.DESCRIBED : request.type();
        ConceptualDomain entity = instantiate(targetType);
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        ConceptualDomain saved = repository.save(entity);
        if (saved instanceof LocalEnumeratedConceptualDomain lecd) {
            applyValueMeanings(lecd, request.valueMeanings());
        }
        versionHistoryService.recordSnapshot(saved, "CREATE");
        return toResponse(saved);
    }

    public ConceptualDomainResponse update(UUID id, ConceptualDomainRequest request) {
        ConceptualDomain entity = loadOrThrow(id);
        ConceptualDomainType fromType = inferType(entity);
        ConceptualDomainType toType = request.type() == null ? fromType : request.type();
        cleanupCrossTypeFields(entity, fromType, toType);
        // 当请求方传入的 type 与实体实际类型不一致时，重建实体为新类型
        // （保留 designations / definitions / sourceUri 由 conceptContentApplier 写入）
        ConceptualDomain mutable = (fromType == toType) ? entity : rehydrate(entity, toType);
        conceptContentApplier.applyConcept(mutable, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        ConceptualDomain saved = repository.save(mutable);
        if (saved instanceof LocalEnumeratedConceptualDomain lecd) {
            applyValueMeanings(lecd, request.valueMeanings());
        } else if (saved instanceof ReferenceEnumeratedConceptualDomain) {
            // 不论 fromType 是什么（尤其从 LOCAL_ENUMERATED 切来），都要清空既有 VM
            // 注意 cleanupCrossTypeFields 已处理 fromType==LOCAL_ENUMERATED 的情形
        }
        versionHistoryService.recordSnapshot(saved, "UPDATE");
        return toResponse(saved);
    }

    private ConceptualDomain instantiate(ConceptualDomainType type) {
        return switch (type) {
            case DESCRIBED -> new DescribedConceptualDomain();
            case LOCAL_ENUMERATED -> new LocalEnumeratedConceptualDomain();
            case REFERENCE_ENUMERATED -> new ReferenceEnumeratedConceptualDomain();
        };
    }

    private ConceptualDomainType inferType(ConceptualDomain entity) {
        if (entity instanceof ReferenceEnumeratedConceptualDomain) return ConceptualDomainType.REFERENCE_ENUMERATED;
        if (entity instanceof LocalEnumeratedConceptualDomain) return ConceptualDomainType.LOCAL_ENUMERATED;
        return ConceptualDomainType.DESCRIBED;
    }

    /**
     * 把 entity 上的核心元数据（designations / definitions / sourceUri / 注册）拷贝到新类型实例上。
     * 新类型独有的字段由后续 applyTypeSpecific 写入。
     */
    private ConceptualDomain rehydrate(ConceptualDomain source, ConceptualDomainType toType) {
        ConceptualDomain target = instantiate(toType);
        target.setId(source.getId());
        target.setSourceUri(source.getSourceUri());
        target.setDesignations(source.getDesignations());
        target.setDefinitions(source.getDefinitions());
        return target;
    }

    private void cleanupCrossTypeFields(ConceptualDomain entity, ConceptualDomainType fromType, ConceptualDomainType toType) {
        if (fromType == toType) {
            return;
        }
        if (entity instanceof LocalEnumeratedConceptualDomain lecd && toType != ConceptualDomainType.LOCAL_ENUMERATED) {
            cleanupValueMeanings(lecd);
        }
        if (toType == ConceptualDomainType.DESCRIBED) {
            entity.setSourceUri(null);
        }
    }

    public void delete(UUID id) {
        ConceptualDomain entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConceptualDomain", id));
        versionHistoryService.recordSnapshot(entity, "DELETE");
        if (entity instanceof LocalEnumeratedConceptualDomain lecd) {
            cleanupValueMeanings(lecd);
        }
        repository.deleteById(id);
    }

    private ConceptualDomain loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConceptualDomain", id));
    }

    private ConceptualDomainResponse toResponse(ConceptualDomain entity) {
        ConceptualDomainType type = inferType(entity);
        List<ValueMeaningResponse> vmResponses = Collections.emptyList();
        if (entity instanceof LocalEnumeratedConceptualDomain lecd) {
            vmResponses = mapValueMeanings(lecd);
        }
        return new ConceptualDomainResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                type,
                entity.getSourceUri(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                vmResponses,
                itemAdministrationService.buildItemMeta(entity));
    }

    /**
     * 按 ConceptualDomainType 过滤的 JPA Specification。
     * - DESCRIBED / LOCAL_ENUMERATED / REFERENCE_ENUMERATED：按实体类型精确匹配
     * - ENUMERATED：等价于"任何枚举子类型"（Local + Reference）
     * - 未指定或无法识别：返回 null（不过滤）
     */
    public static Specification<ConceptualDomain> byType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DESCRIBED" -> (root, query, cb) -> cb.equal(root.type(), DescribedConceptualDomain.class);
            case "LOCAL_ENUMERATED" -> (root, query, cb) -> cb.equal(root.type(), LocalEnumeratedConceptualDomain.class);
            case "REFERENCE_ENUMERATED" -> (root, query, cb) -> cb.equal(root.type(), ReferenceEnumeratedConceptualDomain.class);
            case "ENUMERATED" -> (root, query, cb) -> root.type().in(LocalEnumeratedConceptualDomain.class, ReferenceEnumeratedConceptualDomain.class);
            default -> null;
        };
    }

    private List<ValueMeaningResponse> mapValueMeanings(LocalEnumeratedConceptualDomain lecd) {
        List<ValueMeaningSet> sets = valueMeaningSetRepository
                .findByLocalEnumeratedConceptualDomainId(lecd.getId());
        if (sets.isEmpty()) {
            return Collections.emptyList();
        }
        List<ValueMeaningResponse> out = new ArrayList<>(sets.size());
        for (ValueMeaningSet vms : sets) {
            ValueMeaning vm = vms.getValueMeaning();
            if (vm == null) continue;
            Datetime_Period period = vms.getValidPeriod();
            out.add(new ValueMeaningResponse(
                    vm.getId(),
                    ItemNamingHelper.preferredDesignation(vm),
                    itemAdministrationService.mapDesignations(vm),
                    itemAdministrationService.mapDefinitions(vm),
                    period == null ? null : toLocal(period.getStartDateTime()),
                    period == null ? null : toLocal(period.getEndDateTime()),
                    itemAdministrationService.buildItemMeta(vm)));
        }
        return out;
    }

    /**
     * DTO 边界转换:前端日期时间不含时区(LocalDateTime),按系统默认时区
     * 补齐为 OffsetDateTime(11179-3:2023 6.2.3 Datetime 含时区)。
     */
    private static OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static LocalDateTime toLocal(OffsetDateTime value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private void applyValueMeanings(LocalEnumeratedConceptualDomain lecd, List<ValueMeaningEntry> entries) {
        cleanupValueMeanings(lecd);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<Datetime_Period> periods = new ArrayList<>();
        for (ValueMeaningEntry entry : entries) {
            Datetime_Period p = new Datetime_Period();
            p.setStartDateTime(toOffset(entry.validPeriodStart()));
            p.setEndDateTime(toOffset(entry.validPeriodEnd()));
            periods.add(p);
        }
        ValidPeriodOverlapChecker.checkNoOverlaps(periods);

        for (ValueMeaningEntry entry : entries) {
            ValueMeaning vm = new ValueMeaning();
            itemAdministrationService.applyDesignations(vm, entry.designations());
            itemAdministrationService.applyDefinitions(vm, entry.definitions());
            vm = valueMeaningRepository.save(vm);

            ValueMeaningSet vms = new ValueMeaningSet();
            vms.setValueMeaning(vm);
            vms.setLocalEnumeratedConceptualDomain(lecd);
            Datetime_Period period = new Datetime_Period();
            period.setStartDateTime(toOffset(entry.validPeriodStart()));
            period.setEndDateTime(toOffset(entry.validPeriodEnd()));
            vms.setValidPeriod(period);
            valueMeaningSetRepository.save(vms);
        }
    }

    private void cleanupValueMeanings(LocalEnumeratedConceptualDomain lecd) {
        List<ValueMeaningSet> existing = valueMeaningSetRepository
                .findByLocalEnumeratedConceptualDomainId(lecd.getId());
        for (ValueMeaningSet vms : existing) {
            ValueMeaning vm = vms.getValueMeaning();
            valueMeaningSetRepository.delete(vms);
            if (vm != null) {
                valueMeaningRepository.delete(vm);
            }
        }
    }
}
