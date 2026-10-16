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
        var page0 = ItemListSorts.findAll(repository, spec, pageable);
        var preload0 = itemAdministrationService.preloadItemMeta(page0.getContent());
        return page0.map(entity -> toResponse(entity, preload0));
    }

    @Transactional(readOnly = true)
    public DataTypeSchemeResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public DataTypeSchemeResponse create(DataTypeSchemeRequest request) {
        DataTypeScheme entity = new DataTypeScheme();
        applyRequest(entity, request);
        DataTypeScheme saved = repository.save(entity);
        return toResponse(saved);
    }

    public DataTypeSchemeResponse update(UUID id, DataTypeSchemeRequest request) {
        DataTypeScheme entity = loadOrThrow(id);
        applyRequest(entity, request);
        DataTypeScheme saved = repository.save(entity);
        return toResponse(saved);
    }

    public void delete(UUID id) {
        DataTypeScheme entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataTypeScheme", id));
        // Recorded 及之后的注册状态已纳入版本控制，只能走 Retired，不允许直接删除
        itemAdministrationService.assertDeletable(entity);
        // 先解除所有数据类型的关联（data_type.data_type_scheme_id 外键），
        // 否则删除方案会被引用阻塞（409 数据冲突）。
        entity.getDataTypes().forEach(dt -> dt.setDataTypeScheme(null));
        // 解除提交记录连接；关联条目删光后空的 SubmissionRecord 一并删除
        itemAdministrationService.removeFromSubmissionRecords(entity);
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


        return toResponse(entity, null);


    }



    private DataTypeSchemeResponse toResponse(DataTypeScheme entity, ItemAdministrationService.ItemMetaPreload preload) {
        return new DataTypeSchemeResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                entity.getSchemeReference() == null ? null : entity.getSchemeReference().getId(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity, preload));
    }
}
