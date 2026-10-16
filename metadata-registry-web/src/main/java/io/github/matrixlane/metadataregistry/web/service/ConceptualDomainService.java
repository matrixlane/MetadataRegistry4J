package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Datetime_Period;
import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.Dimensionality;
import io.github.matrixlane.metadataregistry.dataspecification.EnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ValueMeaning;
import io.github.matrixlane.metadataregistry.dataspecification.ValueMeaningSet;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainRequest;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainResponse;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainType;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ValueMeaningEntry;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ValueMeaningResponse;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;
import io.github.matrixlane.metadataregistry.web.repository.DimensionalityRepository;
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
    private final DimensionalityRepository dimensionalityRepository;
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
        var page0 = ItemListSorts.findAll(repository, spec, pageable);
        var preload0 = itemAdministrationService.preloadItemMeta(page0.getContent());
        return page0.map(entity -> toResponse(entity, preload0));
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
        applyValidPeriod(entity, request);
        applyDimensionality(entity, request);
        ConceptualDomain saved = repository.save(entity);
        if (saved instanceof LocalEnumeratedConceptualDomain lecd) {
            applyValueMeanings(lecd, request.valueMeanings());
        }
        versionHistoryService.recordSnapshot(saved, "CREATE", toResponse(saved));
        return toResponse(saved);
    }

    public ConceptualDomainResponse update(UUID id, ConceptualDomainRequest request) {
        ConceptualDomain entity = loadOrThrow(id);
        versionHistoryService.captureBaselineIfEnteringDraft(entity, toResponse(entity));
        ConceptualDomainType fromType = inferType(entity);
        ConceptualDomainType toType = request.type() == null ? fromType : request.type();
        cleanupCrossTypeFields(entity, fromType, toType);
        // 当请求方传入的 type 与实体实际类型不一致时，重建实体为新类型
        // （保留 designations / definitions / sourceUri 由 conceptContentApplier 写入）
        ConceptualDomain mutable = (fromType == toType) ? entity : rehydrate(entity, toType);
        conceptContentApplier.applyConcept(mutable, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        applyValidPeriod(mutable, request);
        applyDimensionality(mutable, request);
        ConceptualDomain saved = repository.save(mutable);
        if (saved instanceof LocalEnumeratedConceptualDomain lecd) {
            applyValueMeanings(lecd, request.valueMeanings());
        } else if (saved instanceof ReferenceEnumeratedConceptualDomain) {
            // 不论 fromType 是什么（尤其从 LOCAL_ENUMERATED 切来），都要清空既有 VM
            // 注意 cleanupCrossTypeFields 已处理 fromType==LOCAL_ENUMERATED 的情形
        }
        versionHistoryService.recordSnapshot(saved, "UPDATE", toResponse(saved));
        return toResponse(saved);
    }

    /**
     * 写入概念域有效期并强制校验起始时间必填（ISO/IEC 11179-31:2023 表 2
     * valid_period 基数为 1，§7.4.2.1.5.1 同 §7.4.2.8.5 约束须含 start_datetime；
     * end 可选，缺省表示仍有效）。
     *
     * <p>请求提供起始时间时整体覆盖该期；仅提供结束时间（会把既有 start 清空）
     * 或两端均未提供且实体尚无起始时间时拒绝。两端均未提供但实体已有起始时间
     * （既有数据已回填）时原样保留，更新不因校验受阻。导入缺有效日期的兜底
     * （以执行时间补 start）见 federation.GenericItemCreator（enforce-valid-period D4），
     * 本条目不默认填充，以免静默吞掉表单漏填。
     */
    private void applyValidPeriod(ConceptualDomain entity, ConceptualDomainRequest request) {
        Datetime_Period period = entity.getValidPeriod() != null ? entity.getValidPeriod() : new Datetime_Period();
        if (request.validPeriodStart() != null) {
            period.setStartDateTime(toOffset(request.validPeriodStart()));
            period.setEndDateTime(toOffset(request.validPeriodEnd()));
        } else if (request.validPeriodEnd() != null || period.getStartDateTime() == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "概念域必须填写有效日期（起始时间）（ISO/IEC 11179-31:2023 表 2 valid_period 基数为 1，"
                            + "§7.4.2.1.5.1 须含 start_datetime）");
        }
        entity.setValidPeriod(period);
    }

    /**
     * 写入概念域维度(dimensionality,0..1):
     * dimensionalityId 非空则校验存在并挂载,空则清除。类型重建(rehydrate)
     * 已在拷贝源实体维度,此处按请求整体覆盖。
     */
    private void applyDimensionality(ConceptualDomain entity, ConceptualDomainRequest request) {
        if (request.dimensionalityId() == null) {
            entity.setDimensionality(null);
            return;
        }
        Dimensionality dimensionality = dimensionalityRepository.findById(request.dimensionalityId())
                .orElseThrow(() -> new ResourceNotFoundException("Dimensionality", request.dimensionalityId()));
        entity.setDimensionality(dimensionality);
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
        target.setValidPeriod(source.getValidPeriod());
        target.setDimensionality(source.getDimensionality());
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
        // Recorded 及之后的注册状态已纳入版本控制，只能走 Retired，不允许直接删除
        itemAdministrationService.assertDeletable(entity);
        versionHistoryService.recordSnapshot(entity, "DELETE", toResponse(entity));
        if (entity instanceof LocalEnumeratedConceptualDomain lecd) {
            cleanupValueMeanings(lecd);
        }
        // 解除提交记录连接；关联条目删光后空的 SubmissionRecord 一并删除
        itemAdministrationService.removeFromSubmissionRecords(entity);
        repository.deleteById(id);
    }

    private ConceptualDomain loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConceptualDomain", id));
    }

    private ConceptualDomainResponse toResponse(ConceptualDomain entity) {


        return toResponse(entity, null);


    }



    private ConceptualDomainResponse toResponse(ConceptualDomain entity, ItemAdministrationService.ItemMetaPreload preload) {
        ConceptualDomainType type = inferType(entity);
        List<ValueMeaningResponse> vmResponses = Collections.emptyList();
        if (entity instanceof LocalEnumeratedConceptualDomain lecd) {
            vmResponses = mapValueMeanings(lecd, preload);
        }
        return new ConceptualDomainResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                type,
                entity.getSourceUri(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                vmResponses,
                entity.getValidPeriod() == null ? null : toLocal(entity.getValidPeriod().getStartDateTime()),
                entity.getValidPeriod() == null ? null : toLocal(entity.getValidPeriod().getEndDateTime()),
                itemAdministrationService.buildItemMeta(entity, preload),
                entity.getDimensionality() == null ? null : entity.getDimensionality().getId(),
                entity.getDimensionality() == null ? null : ItemNamingHelper.preferredDesignation(entity.getDimensionality()));
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

    private List<ValueMeaningResponse> mapValueMeanings(
            LocalEnumeratedConceptualDomain lecd,
            ItemAdministrationService.ItemMetaPreload preload) {
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
                    itemAdministrationService.buildItemMeta(vm, preload)));
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
