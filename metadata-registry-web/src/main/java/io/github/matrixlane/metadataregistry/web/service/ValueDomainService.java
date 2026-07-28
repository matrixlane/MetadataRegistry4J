package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.DataTimePeriod;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValue;
import io.github.matrixlane.metadataregistry.dataspecification.PermissibleValueSet;
import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
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

    @Transactional(readOnly = true)
    public Page<ValueDomainResponse> list(String keyword, Pageable pageable) {
        Specification<ValueDomain> spec = ObjectClassService.byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ValueDomainResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ValueDomainResponse create(ValueDomainRequest request) {
        ValueDomainType type = request.type() == null ? ValueDomainType.DESCRIBED : request.type();
        ValueDomain entity = switch (type) {
            case LOCAL_ENUMERATED -> new LocalEnumeratedValueDomain();
            case DESCRIBED -> new DescribedValueDomain();
        };
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
        applyCommon(entity, request);
        applyTypeSpecific(entity, request);
        ValueDomain saved = repository.save(entity);
        if (saved instanceof LocalEnumeratedValueDomain levd) {
            applyPermissibleValues(levd, request.permissibleValues());
        }
        versionHistoryService.recordSnapshot(saved, "UPDATE");
        return toResponse(saved);
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
        if (request.dataTypeId() != null) {
            DataType dt = dataTypeRepository.findById(request.dataTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("DataType", request.dataTypeId()));
            entity.setDataType(dt);
        } else {
            entity.setDataType(null);
        }
        if (request.unitOfMeasureId() != null) {
            UnitOfMeasure uom = unitOfMeasureRepository.findById(request.unitOfMeasureId())
                    .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", request.unitOfMeasureId()));
            entity.setUnitOfMeasure(uom);
        } else {
            entity.setUnitOfMeasure(null);
        }
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
        }
    }

    private void applyPermissibleValues(LocalEnumeratedValueDomain levd, List<PermissibleValueEntry> entries) {
        cleanupPermissibleValues(levd);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        // Overlap detection
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
        ValueDomainType type = ValueDomainType.DESCRIBED;
        UUID dcdId = null;
        String dcdLabel = null;
        List<PermissibleValueResponse> pvResponses = Collections.emptyList();
        if (entity instanceof LocalEnumeratedValueDomain levd) {
            type = ValueDomainType.LOCAL_ENUMERATED;
            pvResponses = mapPermissibleValues(levd);
        } else if (entity instanceof DescribedValueDomain dvd) {
            DescribedConceptualDomain dcd = dvd.getDescribedConceptualDomain();
            if (dcd != null) {
                dcdId = dcd.getId();
                dcdLabel = ItemNamingHelper.preferredDesignation(dcd);
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
                pvResponses,
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
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
