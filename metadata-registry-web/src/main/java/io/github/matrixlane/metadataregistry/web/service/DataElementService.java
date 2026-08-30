package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.association.AssociationType;
import io.github.matrixlane.metadataregistry.dataspecification.association.MetadataObjectAssociationService;
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
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DataElementResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public DataElementResponse create(DataElementRequest request) {
        DataElement entity = new DataElement();
        applyRequest(entity, request);
        DataElement saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "CREATE");
        return toResponse(saved);
    }

    public DataElementResponse update(UUID id, DataElementRequest request) {
        DataElement entity = loadOrThrow(id);
        applyRequest(entity, request);
        DataElement saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "UPDATE");
        return toResponse(saved);
    }

    public void delete(UUID id) {
        DataElement entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataElement", id));
        versionHistoryService.recordSnapshot(entity, "DELETE");
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
        DataElementConcept dec = entity.getDataElementConcept();
        ValueDomain vd = entity.getValueDomain();
        return new DataElementResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                dec == null ? null : dec.getId(),
                dec == null ? null : ItemNamingHelper.preferredDesignation(dec),
                vd == null ? null : vd.getId(),
                vd == null ? null : ItemNamingHelper.preferredDesignation(vd),
                entity.getPrecision(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }
}
