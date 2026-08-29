package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.DataTimePeriod;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.EnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValue;
import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValueSet;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.association.AssociationType;
import io.github.matrixlane.metadataregistry.dataspecification.association.MetadataObjectAssociationService;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.PermissibleValueEntry;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.PermissibleValueResponse;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.ValueDomainRequest;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.ValueDomainResponse;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.ValueDomainType;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataTypeRepository;
import io.github.matrixlane.metadataregistry.web.repository.PermissibleValueRepository;
import io.github.matrixlane.metadataregistry.web.repository.PermissibleValueSetRepository;
import io.github.matrixlane.metadataregistry.web.repository.UnitOfMeasureRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueDomainRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import io.github.matrixlane.metadataregistry.web.util.ValidPeriodOverlapChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ValueDomainService {

    private final ValueDomainRepository repository;
    private final DataTypeRepository dataTypeRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final ConceptualDomainRepository conceptualDomainRepository;
    private final PermissibleValueRepository permissibleValueRepository;
    private final PermissibleValueSetRepository permissibleValueSetRepository;
    private final ItemAdministrationService itemAdministrationService;
    private final VersionHistoryService versionHistoryService;
    private final MetadataObjectAssociationService associationService = MetadataObjectAssociationService.withDefaults();

    @Transactional(readOnly = true)
    public Page<ValueDomainResponse> list(String keyword, String type, Pageable pageable) {
        Specification<ValueDomain> spec = ObjectClassService.byKeyword(keyword);
        Specification<ValueDomain> typeSpec = byType(type);
        if (typeSpec != null) {
            spec = (spec == null) ? typeSpec : spec.and(typeSpec);
        }
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ValueDomainResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ValueDomainResponse create(ValueDomainRequest request) {
        ValueDomainType targetType = request.type() == null ? ValueDomainType.DESCRIBED : request.type();
        validateReferenceEnumerated(request.permittedValuesFieldname(),
                request.referencedConceptualDomainId(), targetType);
        ValueDomain entity = instantiate(targetType);
        applyCommon(entity, request);
        applyTypeSpecific(entity, request);
        ValueDomain saved = repository.save(entity);
        if (saved instanceof LocalEnumeratedValueDomain levd) {
            applyPermissibleValues(levd, request.permissibleValues());
        }
        versionHistoryService.recordSnapshot(saved, "CREATE");
        return toResponse(saved);
    }

    public ValueDomainResponse update(UUID id, ValueDomainRequest request) {
        ValueDomain entity = loadOrThrow(id);
        ValueDomainType fromType = inferType(entity);
        ValueDomainType toType = request.type() == null ? fromType : request.type();
        validateReferenceEnumerated(request.permittedValuesFieldname(),
                request.referencedConceptualDomainId(), toType);
        cleanupCrossTypeFields(entity, fromType, toType);
        ValueDomain mutable = (fromType == toType) ? entity : rehydrate(entity, toType);
        applyCommon(mutable, request);
        applyTypeSpecific(mutable, request);
        ValueDomain saved = repository.save(mutable);
        if (saved instanceof LocalEnumeratedValueDomain levd) {
            applyPermissibleValues(levd, request.permissibleValues());
        }
        versionHistoryService.recordSnapshot(saved, "UPDATE");
        return toResponse(saved);
    }

    private ValueDomainType inferType(ValueDomain entity) {
        if (entity instanceof ReferenceEnumeratedValueDomain) return ValueDomainType.REFERENCE_ENUMERATED;
        if (entity instanceof LocalEnumeratedValueDomain) return ValueDomainType.LOCAL_ENUMERATED;
        return ValueDomainType.DESCRIBED;
    }

    private ValueDomain instantiate(ValueDomainType type) {
        return switch (type) {
            case DESCRIBED -> new DescribedValueDomain();
            case LOCAL_ENUMERATED -> new LocalEnumeratedValueDomain();
            case REFERENCE_ENUMERATED -> new ReferenceEnumeratedValueDomain();
        };
    }

    private ValueDomain rehydrate(ValueDomain source, ValueDomainType toType) {
        ValueDomain target = instantiate(toType);
        target.setId(source.getId());
        target.setDataType(source.getDataType());
        target.setUnitOfMeasure(source.getUnitOfMeasure());
        target.setMaximumCharacterQuantity(source.getMaximumCharacterQuantity());
        target.setFormat(source.getFormat());
        target.setValidPeriod(source.getValidPeriod());
        target.setDesignations(source.getDesignations());
        target.setDefinitions(source.getDefinitions());
        return target;
    }

    private void validateReferenceEnumerated(String permittedValuesFieldname,
                                             UUID referencedConceptualDomainId,
                                             ValueDomainType type) {
        if (type != ValueDomainType.REFERENCE_ENUMERATED) {
            return;
        }
        if (permittedValuesFieldname == null || permittedValuesFieldname.isBlank()) {
            throw new BusinessException("引用枚举值域必须填写外部源字段名（permittedValuesFieldname）");
        }
        if (referencedConceptualDomainId != null) {
            ConceptualDomainKindChecker.requireReferenceEnumeratedConceptualDomain(
                    conceptualDomainRepository, referencedConceptualDomainId);
        }
    }

    private void cleanupCrossTypeFields(ValueDomain entity, ValueDomainType fromType, ValueDomainType toType) {
        if (fromType == toType) {
            return;
        }
        if (entity instanceof LocalEnumeratedValueDomain levd && toType != ValueDomainType.LOCAL_ENUMERATED) {
            cleanupPermissibleValues(levd);
        }
        if (entity instanceof ReferenceEnumeratedValueDomain revd) {
            revd.setPermittedValuesFieldname(null);
            revd.setReferencedConceptualDomain(null);
        }
        if (entity instanceof DescribedValueDomain dvd && toType != ValueDomainType.DESCRIBED) {
            dvd.setDescribedConceptualDomain(null);
        }
    }

    public void delete(UUID id) {
        ValueDomain entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ValueDomain", id));
        versionHistoryService.recordSnapshot(entity, "DELETE");
        if (entity instanceof LocalEnumeratedValueDomain levd) {
            cleanupPermissibleValues(levd);
        }
        repository.deleteById(id);
    }

    private ValueDomain loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ValueDomain", id));
    }

    private void applyCommon(ValueDomain entity, ValueDomainRequest request) {
        applyDataType(entity, request.dataTypeId());
        applyUnitOfMeasure(entity, request.unitOfMeasureId());
        entity.setMaximumCharacterQuantity(request.maximumCharacterQuantity());
        entity.setFormat(request.format());
        if (request.validPeriodStart() != null || request.validPeriodEnd() != null) {
            DataTimePeriod period = new DataTimePeriod();
            period.setStartDateTime(request.validPeriodStart());
            period.setEndDateTime(request.validPeriodEnd());
            entity.setValidPeriod(period);
        } else {
            entity.setValidPeriod(null);
        }
        itemAdministrationService.applyDesignations(
                entity, request.designations(),
                request.registration() == null ? null : request.registration().namespaceId());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(entity, request.registration());
        }
        associationService.validateCompleteness(entity);
    }

    private void applyDataType(ValueDomain entity, UUID dataTypeId) {
        if (dataTypeId != null) {
            DataType dt = dataTypeRepository.findById(dataTypeId)
                    .orElseThrow(() -> new ResourceNotFoundException("DataType", dataTypeId));
            associationService.associate(AssociationType.VALUE_DOMAIN_TO_DATA_TYPE, entity, dt);
        } else {
            DataType current = entity.getDataType();
            if (current != null) {
                associationService.dissociate(AssociationType.VALUE_DOMAIN_TO_DATA_TYPE, entity, current);
            }
        }
    }

    private void applyUnitOfMeasure(ValueDomain entity, UUID unitOfMeasureId) {
        if (unitOfMeasureId != null) {
            UnitOfMeasure uom = unitOfMeasureRepository.findById(unitOfMeasureId)
                    .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", unitOfMeasureId));
            associationService.associate(AssociationType.VALUE_DOMAIN_TO_UNIT_OF_MEASURE, entity, uom);
        } else {
            UnitOfMeasure current = entity.getUnitOfMeasure();
            if (current != null) {
                associationService.dissociate(AssociationType.VALUE_DOMAIN_TO_UNIT_OF_MEASURE, entity, current);
            }
        }
    }

    private void applyTypeSpecific(ValueDomain entity, ValueDomainRequest request) {
        if (entity instanceof DescribedValueDomain dvd) {
            if (request.describedConceptualDomainId() != null) {
                DescribedConceptualDomain dcd = (DescribedConceptualDomain) conceptualDomainRepository
                        .findById(request.describedConceptualDomainId())
                        .filter(cd -> cd instanceof DescribedConceptualDomain)
                        .orElseThrow(() -> new BusinessException(
                                "describedConceptualDomainId must point to DescribedConceptualDomain"));
                dvd.setDescribedConceptualDomain(dcd);
            } else {
                dvd.setDescribedConceptualDomain(null);
            }
        } else if (entity instanceof ReferenceEnumeratedValueDomain revd) {
            if (request.permittedValuesFieldname() != null) {
                revd.setPermittedValuesFieldname(request.permittedValuesFieldname());
            }
            if (request.referencedConceptualDomainId() != null) {
                ReferenceEnumeratedConceptualDomain recd = conceptualDomainRepository
                        .findById(request.referencedConceptualDomainId())
                        .filter(cd -> cd instanceof ReferenceEnumeratedConceptualDomain)
                        .map(cd -> (ReferenceEnumeratedConceptualDomain) cd)
                        .orElseThrow(() -> new BusinessException(
                                "referencedConceptualDomainId must point to ReferenceEnumeratedConceptualDomain"));
                revd.setReferencedConceptualDomain(recd);
            } else {
                revd.setReferencedConceptualDomain(null);
            }
        }
    }

    private void applyPermissibleValues(LocalEnumeratedValueDomain levd, List<PermissibleValueEntry> entries) {
        cleanupPermissibleValues(levd);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<DataTimePeriod> periods = new ArrayList<>();
        for (PermissibleValueEntry entry : entries) {
            DataTimePeriod p = new DataTimePeriod();
            p.setStartDateTime(entry.validPeriodStart());
            p.setEndDateTime(entry.validPeriodEnd());
            periods.add(p);
        }
        ValidPeriodOverlapChecker.checkNoOverlaps(periods);

        for (PermissibleValueEntry entry : entries) {
            PermissibleValue pv = new PermissibleValue();
            itemAdministrationService.applyDesignations(pv, entry.designations());
            itemAdministrationService.applyDefinitions(pv, entry.definitions());
            pv = permissibleValueRepository.save(pv);

            PermissibleValueSet pvs = new PermissibleValueSet();
            pvs.setPermissibleValue(pv);
            pvs.setLocalEnumeratedValueDomain(levd);
            DataTimePeriod period = new DataTimePeriod();
            period.setStartDateTime(entry.validPeriodStart());
            period.setEndDateTime(entry.validPeriodEnd());
            pvs.setValidPeriod(period);
            permissibleValueSetRepository.save(pvs);
        }
    }

    private void cleanupPermissibleValues(LocalEnumeratedValueDomain levd) {
        List<PermissibleValueSet> existing = permissibleValueSetRepository
                .findByLocalEnumeratedValueDomainId(levd.getId());
        for (PermissibleValueSet pvs : existing) {
            PermissibleValue pv = pvs.getPermissibleValue();
            permissibleValueSetRepository.delete(pvs);
            if (pv != null) {
                permissibleValueRepository.delete(pv);
            }
        }
    }

    private ValueDomainResponse toResponse(ValueDomain entity) {
        ValueDomainType type = inferType(entity);
        UUID dcdId = null;
        String dcdLabel = null;
        String permittedValuesFieldname = null;
        UUID refCdId = null;
        String refCdLabel = null;
        List<PermissibleValueResponse> pvResponses = Collections.emptyList();
        if (entity instanceof LocalEnumeratedValueDomain levd) {
            pvResponses = mapPermissibleValues(levd);
        } else if (entity instanceof DescribedValueDomain dvd) {
            DescribedConceptualDomain dcd = dvd.getDescribedConceptualDomain();
            if (dcd != null) {
                dcdId = dcd.getId();
                dcdLabel = ItemNamingHelper.preferredDesignation(dcd);
            }
        } else if (entity instanceof ReferenceEnumeratedValueDomain revd) {
            permittedValuesFieldname = revd.getPermittedValuesFieldname();
            ReferenceEnumeratedConceptualDomain recd = revd.getReferencedConceptualDomain();
            if (recd != null) {
                refCdId = recd.getId();
                refCdLabel = ItemNamingHelper.preferredDesignation(recd);
            }
        }
        DataType dt = entity.getDataType();
        UnitOfMeasure uom = entity.getUnitOfMeasure();
        DataTimePeriod period = entity.getValidPeriod();
        return new ValueDomainResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                type,
                dt == null ? null : dt.getId(),
                dt == null ? null : ItemNamingHelper.preferredDesignation(dt),
                uom == null ? null : uom.getId(),
                uom == null ? null : ItemNamingHelper.preferredDesignation(uom),
                entity.getMaximumCharacterQuantity(),
                entity.getFormat(),
                period == null ? null : period.getStartDateTime(),
                period == null ? null : period.getEndDateTime(),
                dcdId,
                dcdLabel,
                permittedValuesFieldname,
                refCdId,
                refCdLabel,
                pvResponses,
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }

    /**
     * 按 ValueDomainType 过滤的 JPA Specification。
     * - DESCRIBED / LOCAL_ENUMERATED / REFERENCE_ENUMERATED：按实体类型精确匹配
     * - ENUMERATED：等价于"任何枚举子类型"（Local + Reference）
     * - 未指定或无法识别：返回 null（不过滤）
     */
    public static Specification<ValueDomain> byType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DESCRIBED" -> (root, query, cb) -> cb.equal(root.type(), DescribedValueDomain.class);
            case "LOCAL_ENUMERATED" -> (root, query, cb) -> cb.equal(root.type(), LocalEnumeratedValueDomain.class);
            case "REFERENCE_ENUMERATED" -> (root, query, cb) -> cb.equal(root.type(), ReferenceEnumeratedValueDomain.class);
            case "ENUMERATED" -> (root, query, cb) -> root.type().in(LocalEnumeratedValueDomain.class, ReferenceEnumeratedValueDomain.class);
            default -> null;
        };
    }

    private List<PermissibleValueResponse> mapPermissibleValues(LocalEnumeratedValueDomain levd) {
        List<PermissibleValueSet> sets = permissibleValueSetRepository
                .findByLocalEnumeratedValueDomainId(levd.getId());
        if (sets.isEmpty()) {
            return Collections.emptyList();
        }
        List<PermissibleValueResponse> out = new ArrayList<>(sets.size());
        for (PermissibleValueSet pvs : sets) {
            PermissibleValue pv = pvs.getPermissibleValue();
            if (pv == null) continue;
            DataTimePeriod period = pvs.getValidPeriod();
            out.add(new PermissibleValueResponse(
                    pv.getId(),
                    ItemNamingHelper.preferredDesignation(pv),
                    itemAdministrationService.mapDesignations(pv),
                    itemAdministrationService.mapDefinitions(pv),
                    period == null ? null : period.getStartDateTime(),
                    period == null ? null : period.getEndDateTime(),
                    itemAdministrationService.buildItemMeta(pv)));
        }
        return out;
    }
}
