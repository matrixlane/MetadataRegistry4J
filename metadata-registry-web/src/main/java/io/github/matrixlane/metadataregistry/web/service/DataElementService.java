package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.web.service.association.AssociationType;
import io.github.matrixlane.metadataregistry.web.service.association.MetadataObjectAssociationService;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementResponse;
import io.github.matrixlane.metadataregistry.web.repository.DataElementConceptRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueDomainRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DataElementService {

    private final DataElementRepository repository;
    private final DataElementConceptRepository dataElementConceptRepository;
    private final ValueDomainRepository valueDomainRepository;
    private final ItemAdministrationService itemAdministrationService;
    private final VersionHistoryService versionHistoryService;
    private final MetadataObjectAssociationService associationService = MetadataObjectAssociationService.withDefaults();

    @Transactional(readOnly = true)
    public Page<DataElementResponse> list(String keyword, Pageable pageable) {
        Specification<DataElement> spec = ObjectClassService.byKeyword(keyword);
        var page0 = ItemListSorts.findAll(repository, spec, pageable);
        var preload0 = itemAdministrationService.preloadItemMeta(page0.getContent());
        // 批量预取本页条目 + 关联 DEC/VD 的首选指称：取代理 id 不初始化实体
        // （初始化即级联触发 identifiedItem 的 JOINED 继承重 join），标签改走标量查询。
        Map<UUID, String> names = itemAdministrationService.preloadPreferredDesignations(
                collectRelatedIds(page0.getContent()));
        return page0.map(entity -> toResponse(entity, preload0, names));
    }

    @Transactional(readOnly = true)
    public DataElementResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public DataElementResponse create(DataElementRequest request) {
        DataElement entity = new DataElement();
        applyRequest(entity, request);
        DataElement saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "CREATE", toResponse(saved));
        return toResponse(saved);
    }

    public DataElementResponse update(UUID id, DataElementRequest request) {
        DataElement entity = loadOrThrow(id);
        versionHistoryService.captureBaselineIfEnteringDraft(entity, toResponse(entity));
        applyRequest(entity, request);
        DataElement saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "UPDATE", toResponse(saved));
        return toResponse(saved);
    }

    public void delete(UUID id) {
        DataElement entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataElement", id));
        // Recorded 及之后的注册状态已纳入版本控制，只能走 Retired，不允许直接删除
        itemAdministrationService.assertDeletable(entity);
        versionHistoryService.recordSnapshot(entity, "DELETE", toResponse(entity));
        // 解除提交记录连接；关联条目删光后空的 SubmissionRecord 一并删除
        itemAdministrationService.removeFromSubmissionRecords(entity);
        repository.deleteById(id);
    }

    private DataElement loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataElement", id));
    }

    private void applyRequest(DataElement entity, DataElementRequest request) {
        applyDataElementConcept(entity, request.dataElementConceptId());
        applyValueDomain(entity, request.valueDomainId());
        entity.setPrecision(request.precision());
        itemAdministrationService.applyDesignations(
                entity, request.designations(),
                request.registration() == null ? null : request.registration().namespaceId());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(entity, request.registration());
        }
        associationService.validateCompleteness(entity);
    }

    private void applyDataElementConcept(DataElement entity, UUID dataElementConceptId) {
        if (dataElementConceptId != null) {
            DataElementConcept dec = dataElementConceptRepository.findById(dataElementConceptId)
                    .orElseThrow(() -> new ResourceNotFoundException("DataElementConcept", dataElementConceptId));
            associationService.associate(AssociationType.DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT, entity, dec);
        } else {
            DataElementConcept current = entity.getDataElementConcept();
            if (current != null) {
                associationService.dissociate(AssociationType.DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT, entity, current);
            }
        }
    }

    private void applyValueDomain(DataElement entity, UUID valueDomainId) {
        if (valueDomainId != null) {
            ValueDomain vd = valueDomainRepository.findById(valueDomainId)
                    .orElseThrow(() -> new ResourceNotFoundException("ValueDomain", valueDomainId));
            associationService.associate(AssociationType.DATA_ELEMENT_TO_VALUE_DOMAIN, entity, vd);
        } else {
            ValueDomain current = entity.getValueDomain();
            if (current != null) {
                associationService.dissociate(AssociationType.DATA_ELEMENT_TO_VALUE_DOMAIN, entity, current);
            }
        }
    }

    private DataElementResponse toResponse(DataElement entity) {
        return toResponse(entity, null, null);
    }

    private DataElementResponse toResponse(DataElement entity,
                                           ItemAdministrationService.ItemMetaPreload preload,
                                           Map<UUID, String> preferredNames) {
        DataElementConcept dec = entity.getDataElementConcept();
        ValueDomain vd = entity.getValueDomain();
        UUID decId = dec == null ? null : dec.getId();
        UUID vdId = vd == null ? null : vd.getId();
        return new DataElementResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                decId,
                decId == null ? null : resolveName(decId, dec, preferredNames),
                vdId,
                vdId == null ? null : resolveName(vdId, vd, preferredNames),
                entity.getPrecision(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity, preload,
                        /* includeSubmissionRecords */ preload == null));
    }

    /**
     * 解析关联条目标签：批量预取名存在时直接取（列表路径，纯标量不初始化代理）；
     * 否则回退到实体路径 {@link ItemNamingHelper#preferredDesignation}（单条/编辑路径）。
     */
    private static String resolveName(UUID id, io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item item,
                                      Map<UUID, String> preferredNames) {
        if (preferredNames != null) {
            return preferredNames.get(id);
        }
        return ItemNamingHelper.preferredDesignation(item);
    }

    private static Set<UUID> collectRelatedIds(java.util.List<DataElement> content) {
        Set<UUID> ids = new HashSet<>();
        for (DataElement de : content) {
            ids.add(de.getId());
            DataElementConcept dec = de.getDataElementConcept();
            if (dec != null) {
                ids.add(dec.getId());
            }
            ValueDomain vd = de.getValueDomain();
            if (vd != null) {
                ids.add(vd.getId());
            }
        }
        return ids;
    }
}
