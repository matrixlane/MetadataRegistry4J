package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.DataTypeScheme;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.datatype.DataTypeSchemeRequest;
import io.github.matrixlane.metadataregistry.web.dto.datatype.DataTypeSchemeResponse;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.web.repository.DataTypeSchemeRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
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
public class DataTypeSchemeService {

    private final DataTypeSchemeRepository repository;
    private final ReferenceDocumentRepository referenceDocumentRepository;
    private final ItemAdministrationService itemAdministrationService;

    @Transactional(readOnly = true)
    public Page<DataTypeSchemeResponse> list(String keyword, Pageable pageable) {
        Specification<DataTypeScheme> spec = ObjectClassService.byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DataTypeSchemeResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public DataTypeSchemeResponse create(DataTypeSchemeRequest request) {
        DataTypeScheme entity = new DataTypeScheme();
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public DataTypeSchemeResponse update(UUID id, DataTypeSchemeRequest request) {
        DataTypeScheme entity = loadOrThrow(id);
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("DataTypeScheme", id);
        }
        repository.deleteById(id);
    }

    private DataTypeScheme loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataTypeScheme", id));
    }

    private void applyRequest(DataTypeScheme entity, DataTypeSchemeRequest request) {
        entity.setSchemeReference(request.schemeReferenceId() == null ? null
                : referenceDocumentRepository.findById(request.schemeReferenceId())
                        .orElseThrow(() -> new ResourceNotFoundException("ReferenceDocument", request.schemeReferenceId())));
        itemAdministrationService.applyDesignations(
                entity, request.designations(),
                request.registration() == null ? null : request.registration().namespaceId());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(entity, request.registration());
        }
    }

    private DataTypeSchemeResponse toResponse(DataTypeScheme entity) {
        return new DataTypeSchemeResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                entity.getSchemeReference() == null ? null : entity.getSchemeReference().getId(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }
}
