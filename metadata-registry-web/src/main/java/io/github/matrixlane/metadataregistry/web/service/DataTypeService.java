package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.DataTypeScheme;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.datatype.DataTypeRequest;
import io.github.matrixlane.metadataregistry.web.dto.datatype.DataTypeResponse;
import io.github.matrixlane.metadataregistry.web.repository.DataTypeRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataTypeSchemeRepository;
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
public class DataTypeService {

    private final DataTypeRepository repository;
    private final DataTypeSchemeRepository dataTypeSchemeRepository;
    private final ItemAdministrationService itemAdministrationService;
    private final VersionHistoryService versionHistoryService;

    @Transactional(readOnly = true)
    public Page<DataTypeResponse> list(String keyword, Pageable pageable) {
        Specification<DataType> spec = ObjectClassService.byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DataTypeResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public DataTypeResponse create(DataTypeRequest request) {
        DataType entity = new DataType();
        applyRequest(entity, request);
        DataType saved = repository.save(entity);
        itemAdministrationService.recordSubmission(saved, request.registration());
        versionHistoryService.recordSnapshot(saved, "CREATE", toResponse(saved));
        return toResponse(saved);
    }

    public DataTypeResponse update(UUID id, DataTypeRequest request) {
        DataType entity = loadOrThrow(id);
        versionHistoryService.captureBaselineIfEnteringDraft(entity, toResponse(entity));
        applyRequest(entity, request);
        DataType saved = repository.save(entity);
        itemAdministrationService.recordSubmission(saved, request.registration());
        versionHistoryService.recordSnapshot(saved, "UPDATE", toResponse(saved));
        return toResponse(saved);
    }

    public void delete(UUID id) {
        DataType entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataType", id));
        versionHistoryService.recordSnapshot(entity, "DELETE", toResponse(entity));
        repository.deleteById(id);
    }

    private DataType loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataType", id));
    }

    private void applyRequest(DataType entity, DataTypeRequest request) {
        entity.setAnnotation(request.annotation());
        if (request.dataTypeSchemeId() != null) {
            DataTypeScheme scheme = dataTypeSchemeRepository.findById(request.dataTypeSchemeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "DataTypeScheme", request.dataTypeSchemeId()));
            entity.setDataTypeScheme(scheme);
        } else {
            entity.setDataTypeScheme(null);
        }
        itemAdministrationService.applyDesignations(
                entity, request.designations(),
                request.registration() == null ? null : request.registration().namespaceId());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(entity, request.registration());
        }
    }

    private DataTypeResponse toResponse(DataType entity) {
        DataTypeScheme scheme = entity.getDataTypeScheme();
        return new DataTypeResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                scheme == null ? null : scheme.getId(),
                scheme == null ? null : ItemNamingHelper.preferredDesignation(scheme),
                entity.getAnnotation(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }
}
