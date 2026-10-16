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
        var page0 = ItemListSorts.findAll(repository, spec, pageable);
        var preload0 = itemAdministrationService.preloadItemMeta(page0.getContent());
        return page0.map(entity -> toResponse(entity, preload0));
    }

    @Transactional(readOnly = true)
    public DataTypeResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public DataTypeResponse create(DataTypeRequest request) {
        DataType entity = new DataType();
        applyRequest(entity, request);
        DataType saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "CREATE", toResponse(saved));
        return toResponse(saved);
    }

    public DataTypeResponse update(UUID id, DataTypeRequest request) {
        DataType entity = loadOrThrow(id);
        versionHistoryService.captureBaselineIfEnteringDraft(entity, toResponse(entity));
        applyRequest(entity, request);
        DataType saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "UPDATE", toResponse(saved));
        return toResponse(saved);
    }

    public void delete(UUID id) {
        DataType entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataType", id));
        // Recorded 及之后的注册状态已纳入版本控制，只能走 Retired，不允许直接删除
        itemAdministrationService.assertDeletable(entity);
        versionHistoryService.recordSnapshot(entity, "DELETE", toResponse(entity));
        // 解除提交记录连接；关联条目删光后空的 SubmissionRecord 一并删除
        itemAdministrationService.removeFromSubmissionRecords(entity);
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


        return toResponse(entity, null);


    }



    private DataTypeResponse toResponse(DataType entity, ItemAdministrationService.ItemMetaPreload preload) {
        DataTypeScheme scheme = entity.getDataTypeScheme();
        return new DataTypeResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                scheme == null ? null : scheme.getId(),
                scheme == null ? null : ItemNamingHelper.preferredDesignation(scheme),
                entity.getAnnotation(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity, preload));
    }
}
