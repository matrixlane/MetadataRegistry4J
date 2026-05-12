package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.dataspecification.Property;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.dec.DataElementConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.dec.DataElementConceptResponse;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DataElementConceptService {

    private final DataElementConceptRepository repository;
    private final ObjectClassRepository objectClassRepository;
    private final PropertyRepository propertyRepository;
    private final ConceptContentApplier conceptContentApplier;
    private final ItemAdministrationService itemAdministrationService;

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
        return toResponse(repository.save(entity));
    }

    public DataElementConceptResponse update(UUID id, DataElementConceptRequest request) {
        DataElementConcept entity = loadOrThrow(id);
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("DataElementConcept", id);
        }
        repository.deleteById(id);
    }

    private DataElementConcept loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataElementConcept", id));
    }

    private void applyRequest(DataElementConcept entity, DataElementConceptRequest request) {
        if (request.objectClassId() != null) {
            ObjectClass oc = objectClassRepository.findById(request.objectClassId())
                    .orElseThrow(() -> new ResourceNotFoundException("ObjectClass", request.objectClassId()));
            entity.setObjectClass(oc);
        } else {
            entity.setObjectClass(null);
        }
        if (request.propertyId() != null) {
            Property p = propertyRepository.findById(request.propertyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Property", request.propertyId()));
            entity.setProperty(p);
        } else {
            entity.setProperty(null);
        }
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
    }

    private DataElementConceptResponse toResponse(DataElementConcept entity) {
        ObjectClass oc = entity.getObjectClass();
        Property prop = entity.getProperty();
        return new DataElementConceptResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                oc == null ? null : oc.getId(),
                oc == null ? null : ItemNamingHelper.preferredDesignation(oc),
                prop == null ? null : prop.getId(),
                prop == null ? null : ItemNamingHelper.preferredDesignation(prop),
                entity.getSourceUri(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }
}
