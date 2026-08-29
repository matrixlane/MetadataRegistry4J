package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.dataspecification.Property;
import io.github.matrixlane.metadataregistry.dataspecification.association.AssociationType;
import io.github.matrixlane.metadataregistry.dataspecification.association.MetadataObjectAssociationService;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.dec.ConceptualDomainKindLabel;
import io.github.matrixlane.metadataregistry.web.dto.dec.DataElementConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.dec.DataElementConceptResponse;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainType;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataElementConceptRepository;
import io.github.matrixlane.metadataregistry.web.repository.ObjectClassRepository;
import io.github.matrixlane.metadataregistry.web.repository.PropertyRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DataElementConceptService {

    private final DataElementConceptRepository repository;
    private final ObjectClassRepository objectClassRepository;
    private final PropertyRepository propertyRepository;
    private final ConceptualDomainRepository conceptualDomainRepository;
    private final ConceptContentApplier conceptContentApplier;
    private final ItemAdministrationService itemAdministrationService;
    private final VersionHistoryService versionHistoryService;
    private final MetadataObjectAssociationService associationService = MetadataObjectAssociationService.withDefaults();

    @Transactional(readOnly = true)
    public Page<DataElementConceptResponse> list(String keyword, Pageable pageable) {
        Specification<DataElementConcept> spec = ObjectClassService.byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DataElementConceptResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public DataElementConceptResponse create(DataElementConceptRequest request) {
        DataElementConcept entity = new DataElementConcept();
        applyRequest(entity, request);
        DataElementConcept saved = repository.save(entity);
        applyConceptualDomainLinks(saved, request.conceptualDomainIds());
        versionHistoryService.recordSnapshot(saved, "CREATE");
        return toResponse(saved);
    }

    public DataElementConceptResponse update(UUID id, DataElementConceptRequest request) {
        DataElementConcept entity = loadOrThrow(id);
        applyRequest(entity, request);
        applyConceptualDomainLinks(entity, request.conceptualDomainIds());
        DataElementConcept saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "UPDATE");
        return toResponse(saved);
    }

    public void delete(UUID id) {
        DataElementConcept entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataElementConcept", id));
        // 先清理多对多反向引用，再删除实体
        clearConceptualDomainLinks(entity);
        versionHistoryService.recordSnapshot(entity, "DELETE");
        repository.deleteById(id);
    }

    private DataElementConcept loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataElementConcept", id));
    }

    private void applyRequest(DataElementConcept entity, DataElementConceptRequest request) {
        applyObjectClass(entity, request.objectClassId());
        applyProperty(entity, request.propertyId());
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        associationService.validateCompleteness(entity);
    }

    private void applyObjectClass(DataElementConcept entity, UUID objectClassId) {
        if (objectClassId != null) {
            ObjectClass oc = objectClassRepository.findById(objectClassId)
                    .orElseThrow(() -> new ResourceNotFoundException("ObjectClass", objectClassId));
            associationService.associate(AssociationType.DATA_ELEMENT_CONCEPT_TO_OBJECT_CLASS, entity, oc);
        } else {
            ObjectClass current = entity.getObjectClass();
            if (current != null) {
                associationService.dissociate(AssociationType.DATA_ELEMENT_CONCEPT_TO_OBJECT_CLASS, entity, current);
            }
        }
    }

    private void applyProperty(DataElementConcept entity, UUID propertyId) {
        if (propertyId != null) {
            Property p = propertyRepository.findById(propertyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));
            associationService.associate(AssociationType.DATA_ELEMENT_CONCEPT_TO_PROPERTY, entity, p);
        } else {
            Property current = entity.getProperty();
            if (current != null) {
                associationService.dissociate(AssociationType.DATA_ELEMENT_CONCEPT_TO_PROPERTY, entity, current);
            }
        }
    }

    /**
     * 多对多重建：DataElementConcept.conceptualDomains 是 mappedBy 反向端，
     * 由 ConceptualDomain.dataElementConcepts 持有 join 表。本期实现以 ConceptualDomain 端为权威重建。
     */
    private void applyConceptualDomainLinks(DataElementConcept dec, List<UUID> conceptualDomainIds) {
        // 1. 先把旧关联清空（按反向端）
        clearConceptualDomainLinks(dec);
        if (conceptualDomainIds == null || conceptualDomainIds.isEmpty()) {
            return;
        }
        // 2. 按新 IDs 加载并挂回
        Set<UUID> uniqueIds = new HashSet<>(conceptualDomainIds);
        for (UUID cdId : uniqueIds) {
            ConceptualDomain cd = conceptualDomainRepository.findById(cdId)
                    .orElseThrow(() -> new ResourceNotFoundException("ConceptualDomain", cdId));
            associationService.associate(AssociationType.DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN, dec, cd);
        }
        conceptualDomainRepository.saveAll(dec.getConceptualDomains());
    }

    private void clearConceptualDomainLinks(DataElementConcept dec) {
        List<ConceptualDomain> existing = dec.getConceptualDomains();
        if (existing == null || existing.isEmpty()) {
            return;
        }
        for (ConceptualDomain cd : new ArrayList<>(existing)) {
            associationService.dissociate(AssociationType.DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN, dec, cd);
        }
        conceptualDomainRepository.saveAll(existing);
    }

    private DataElementConceptResponse toResponse(DataElementConcept entity) {
        ObjectClass oc = entity.getObjectClass();
        Property prop = entity.getProperty();
        List<ConceptualDomain> domains = entity.getConceptualDomains();
        List<UUID> cdIds = new ArrayList<>();
        List<ConceptualDomainKindLabel> cdLabels = new ArrayList<>();
        if (domains != null) {
            for (ConceptualDomain cd : domains) {
                cdIds.add(cd.getId());
                cdLabels.add(new ConceptualDomainKindLabel(
                        cd.getId(),
                        ItemNamingHelper.preferredDesignation(cd),
                        inferConceptualDomainType(cd)));
            }
        }
        return new DataElementConceptResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                oc == null ? null : oc.getId(),
                oc == null ? null : ItemNamingHelper.preferredDesignation(oc),
                prop == null ? null : prop.getId(),
                prop == null ? null : ItemNamingHelper.preferredDesignation(prop),
                entity.getSourceUri(),
                cdIds,
                cdLabels,
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }

    private ConceptualDomainType inferConceptualDomainType(ConceptualDomain cd) {
        String simple = cd.getClass().getSimpleName();
        return switch (simple) {
            case "DescribedConceptualDomain" -> ConceptualDomainType.DESCRIBED;
            case "LocalEnumeratedConceptualDomain" -> ConceptualDomainType.LOCAL_ENUMERATED;
            case "ReferenceEnumeratedConceptualDomain" -> ConceptualDomainType.REFERENCE_ENUMERATED;
            default -> ConceptualDomainType.DESCRIBED;
        };
    }
}