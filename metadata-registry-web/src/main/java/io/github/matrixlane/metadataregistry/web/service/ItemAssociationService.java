package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.web.service.association.AssociationType;
import io.github.matrixlane.metadataregistry.web.service.association.MetadataObjectAssociationService;
import io.github.matrixlane.metadataregistry.web.service.association.ValueDomainConceptualDomains;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.association.AssociationMutationRequest;
import io.github.matrixlane.metadataregistry.web.dto.association.ItemAssociationEntry;
import io.github.matrixlane.metadataregistry.web.dto.association.ItemAssociationsResponse;
import io.github.matrixlane.metadataregistry.web.dto.association.StandardAssociationType;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataElementConceptRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueDomainRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 四实体（数据元 / 数据元概念 / 概念域 / 值域）标准关联的查询与维护。
 * 关联集合与多重性以 ISO/IEC 11179-31:2023 为准：
 * data_element_meaning (7.2.3.3/7.6.3.2) DE 0..* -> 0..1 DEC;
 * data_element_domain (7.2.3.2/7.6.3.1) DE 0..* -> 0..1 VD;
 * value_domain_meaning (7.2.3.1/7.4.4.1) VD 0..* -> 0..1 CD;
 * data_element_concept_domain (7.2.3.4) DEC 0..* <-> 0..* CD.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ItemAssociationService {

    private final DataElementRepository dataElementRepository;
    private final DataElementConceptRepository dataElementConceptRepository;
    private final ConceptualDomainRepository conceptualDomainRepository;
    private final ValueDomainRepository valueDomainRepository;
    private final AdministeredItemRepository administeredItemRepository;
    private final ItemAdministrationService itemAdministrationService;
    private final VersionHistoryService versionHistoryService;
    private final MetadataObjectAssociationService associationService = MetadataObjectAssociationService.withDefaults();

    /**
     * 汇集焦点实体在四实体标准关联上的全部正向与反向实例。
     */
    @Transactional(readOnly = true)
    public ItemAssociationsResponse listAssociations(UUID itemId) {
        List<ItemAssociationEntry> out = new ArrayList<>();
        String itemType;
        DataElement de = dataElementRepository.findById(itemId).orElse(null);
        if (de != null) {
            itemType = "DataElement";
            if (de.getDataElementConcept() != null) {
                out.add(entry(StandardAssociationType.DATA_ELEMENT_MEANING, ItemAssociationEntry.OUTGOING,
                        de.getDataElementConcept(), "DataElementConcept"));
            }
            if (de.getValueDomain() != null) {
                out.add(entry(StandardAssociationType.DATA_ELEMENT_DOMAIN, ItemAssociationEntry.OUTGOING,
                        de.getValueDomain(), "ValueDomain"));
            }
            return new ItemAssociationsResponse(itemId, itemType, out);
        }
        DataElementConcept dec = dataElementConceptRepository.findById(itemId).orElse(null);
        if (dec != null) {
            itemType = "DataElementConcept";
            for (ConceptualDomain cd : safeList(dec.getConceptualDomains())) {
                out.add(entry(StandardAssociationType.DATA_ELEMENT_CONCEPT_DOMAIN, ItemAssociationEntry.OUTGOING,
                        cd, "ConceptualDomain"));
            }
            for (DataElement linked : dataElementRepository.findByDataElementConcept_Id(itemId)) {
                out.add(entry(StandardAssociationType.DATA_ELEMENT_MEANING, ItemAssociationEntry.INCOMING,
                        linked, "DataElement"));
            }
            return new ItemAssociationsResponse(itemId, itemType, out);
        }
        ValueDomain vd = valueDomainRepository.findById(itemId).orElse(null);
        if (vd != null) {
            itemType = "ValueDomain";
            ConceptualDomain cd = ValueDomainConceptualDomains.get(vd);
            if (cd != null) {
                out.add(entry(StandardAssociationType.VALUE_DOMAIN_MEANING, ItemAssociationEntry.OUTGOING,
                        cd, "ConceptualDomain"));
            }
            for (DataElement linked : dataElementRepository.findByValueDomain_Id(itemId)) {
                out.add(entry(StandardAssociationType.DATA_ELEMENT_DOMAIN, ItemAssociationEntry.INCOMING,
                        linked, "DataElement"));
            }
            return new ItemAssociationsResponse(itemId, itemType, out);
        }
        ConceptualDomain cd = conceptualDomainRepository.findById(itemId).orElse(null);
        if (cd != null) {
            itemType = "ConceptualDomain";
            for (DataElementConcept linked : safeList(cd.getDataElementConcepts())) {
                out.add(entry(StandardAssociationType.DATA_ELEMENT_CONCEPT_DOMAIN, ItemAssociationEntry.OUTGOING,
                        linked, "DataElementConcept"));
            }
            Set<UUID> seen = new LinkedHashSet<>();
            List<ValueDomain> vds = new ArrayList<>();
            vds.addAll(valueDomainRepository.findDescribedByConceptualDomainId(itemId));
            vds.addAll(valueDomainRepository.findLocalEnumeratedByConceptualDomainId(itemId));
            vds.addAll(valueDomainRepository.findReferenceEnumeratedByConceptualDomainId(itemId));
            for (ValueDomain linked : vds) {
                if (seen.add(linked.getId())) {
                    out.add(entry(StandardAssociationType.VALUE_DOMAIN_MEANING, ItemAssociationEntry.INCOMING,
                            linked, "ValueDomain"));
                }
            }
            return new ItemAssociationsResponse(itemId, itemType, out);
        }
        throw new ResourceNotFoundException("Item", itemId);
    }

    /**
     * 按关联类型的标准方向建立关联。
     */
    public ItemAssociationsResponse associate(StandardAssociationType type, AssociationMutationRequest request) {
        apply(type, request, true);
        return listAssociations(request.sourceItemId());
    }

    /**
     * 按关联类型的标准方向解除关联。
     */
    public ItemAssociationsResponse dissociate(StandardAssociationType type, AssociationMutationRequest request) {
        apply(type, request, false);
        return listAssociations(request.sourceItemId());
    }

    private void apply(StandardAssociationType type, AssociationMutationRequest request, boolean establish) {
        UUID sourceId = request.sourceItemId();
        UUID targetId = request.targetItemId();
        switch (type) {
            case DATA_ELEMENT_MEANING -> {
                DataElement de = loadDataElement(sourceId);
                DataElementConcept dec = dataElementConceptRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("DataElementConcept", targetId));
                if (establish) {
                    associationService.associate(AssociationType.DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT, de, dec);
                } else {
                    associationService.dissociate(AssociationType.DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT, de, dec);
                }
                dataElementRepository.save(de);
                versionHistoryService.recordSnapshot(de, "UPDATE");
            }
            case DATA_ELEMENT_DOMAIN -> {
                DataElement de = loadDataElement(sourceId);
                ValueDomain vd = valueDomainRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("ValueDomain", targetId));
                if (establish) {
                    associationService.associate(AssociationType.DATA_ELEMENT_TO_VALUE_DOMAIN, de, vd);
                } else {
                    associationService.dissociate(AssociationType.DATA_ELEMENT_TO_VALUE_DOMAIN, de, vd);
                }
                dataElementRepository.save(de);
                versionHistoryService.recordSnapshot(de, "UPDATE");
            }
            case VALUE_DOMAIN_MEANING -> {
                ValueDomain vd = valueDomainRepository.findById(sourceId)
                        .orElseThrow(() -> new ResourceNotFoundException("ValueDomain", sourceId));
                ConceptualDomain cd = conceptualDomainRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("ConceptualDomain", targetId));
                if (establish) {
                    associationService.associate(AssociationType.VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN, vd, cd);
                } else {
                    associationService.dissociate(AssociationType.VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN, vd, cd);
                }
                valueDomainRepository.save(vd);
                versionHistoryService.recordSnapshot(vd, "UPDATE");
            }
            case DATA_ELEMENT_CONCEPT_DOMAIN -> {
                DataElementConcept dec = dataElementConceptRepository.findById(sourceId)
                        .orElseThrow(() -> new ResourceNotFoundException("DataElementConcept", sourceId));
                ConceptualDomain cd = conceptualDomainRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("ConceptualDomain", targetId));
                if (establish) {
                    associationService.associate(AssociationType.DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN, dec, cd);
                } else {
                    associationService.dissociate(AssociationType.DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN, dec, cd);
                }
                conceptualDomainRepository.save(cd);
                versionHistoryService.recordSnapshot(dec, "UPDATE");
            }
            default -> throw new BusinessException("不支持的关联类型：" + type);
        }
    }

    private DataElement loadDataElement(UUID id) {
        return dataElementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataElement", id));
    }

    private ItemAssociationEntry entry(StandardAssociationType type, String direction, Item target, String targetType) {
        ItemMetaResponse meta = itemAdministrationService.buildItemMeta(target);
        LocalDateTime created = null;
        LocalDateTime changed = null;
        AdministeredItem administered = administeredItemRepository.findByItem_Id(target.getId()).orElse(null);
        if (administered != null) {
            created = administered.getCreationDateTime();
            changed = administered.getLastChangeDateTime();
        }
        return new ItemAssociationEntry(
                type.name(),
                direction,
                target.getId(),
                targetType,
                ItemNamingHelper.preferredDesignation(target),
                meta == null ? null : meta.identifier(),
                meta == null ? null : meta.version(),
                created,
                changed);
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }
}
