package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.concept.ConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.concept.ConceptResponse;
import io.github.matrixlane.metadataregistry.web.repository.ObjectClassRepository;
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
public class ObjectClassService {

    private final ObjectClassRepository repository;
    private final ConceptContentApplier conceptContentApplier;
    private final ItemAdministrationService itemAdministrationService;
    private final VersionHistoryService versionHistoryService;

    @Transactional(readOnly = true)
    public Page<ConceptResponse> list(String keyword, Pageable pageable) {
        Specification<ObjectClass> spec = byKeyword(keyword);
        return ItemListSorts.findAll(repository, spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ConceptResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ConceptResponse create(ConceptRequest request) {
        ObjectClass entity = new ObjectClass();
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        ObjectClass saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "CREATE", toResponse(saved));
        return toResponse(saved);
    }

    public ConceptResponse update(UUID id, ConceptRequest request) {
        ObjectClass entity = loadOrThrow(id);
        versionHistoryService.captureBaselineIfEnteringDraft(entity, toResponse(entity));
        conceptContentApplier.applyConcept(entity, request.sourceUri(),
                request.designations(), request.definitions(), request.registration());
        ObjectClass saved = repository.save(entity);
        versionHistoryService.recordSnapshot(saved, "UPDATE", toResponse(saved));
        return toResponse(saved);
    }

    public void delete(UUID id) {
        ObjectClass entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ObjectClass", id));
        // Recorded 及之后的注册状态已纳入版本控制，只能走 Retired，不允许直接删除
        itemAdministrationService.assertDeletable(entity);
        versionHistoryService.recordSnapshot(entity, "DELETE", toResponse(entity));
        // 解除提交记录连接；关联条目删光后空的 SubmissionRecord 一并删除
        itemAdministrationService.removeFromSubmissionRecords(entity);
        repository.deleteById(id);
    }

    private ObjectClass loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ObjectClass", id));
    }

    /**
     * 列表页关键词检索：名称（指称）与标识符（Scoped_Identifier）任一命中即返回。
     *
     * <p>两个条件各自走 exists 子查询：Item 的 designations 是集合 join，一条 Item
     * 命中多条指称会放大结果基数，distinct 只能去重不能修正计数；exists 语义精确。
     * 标识符侧沿受管链回挂到同一个 Item：
     * {@code Scoped_Identifier → Identified_Item(Registered_Item) → item}。
     * 未注册（无 Identified_Item）的 Item 自然不参与标识符匹配，名称匹配不受影响。</p>
     *
     * <p>大小写不敏感沿用既有 {@code lower() + like %..%} 语义。</p>
     *
     * <p>不得对本查询施加 {@code distinct}：EXISTS 谓词不放大结果基数，去重毫无必要；
     * 且列表排序经 {@link ItemListSorts} 改为标量子查询后，若叠加 distinct，
     * SQL Server 会拒绝「ORDER BY 项不在 DISTINCT 选择列表中」（错误 145）。</p>
     */
    static <T extends Item> Specification<T> byKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return Specification.where(null);
        String like = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> {
            // 名称（指称）
            jakarta.persistence.criteria.Subquery<Long> sqName = query.subquery(Long.class);
            var designation = sqName.from(
                io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation.class);
            sqName.select(cb.literal(1L));
            sqName.where(cb.and(
                cb.equal(designation.get("item").get("id"), root.get("id")),
                cb.like(cb.lower(designation.get("sign").get("value")), like)));
            // 标识符（Scoped_Identifier）
            jakarta.persistence.criteria.Subquery<Long> sqIdentifier = query.subquery(Long.class);
            var scopedIdentifier = sqIdentifier.from(
                io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier.class);
            sqIdentifier.select(cb.literal(1L));
            sqIdentifier.where(cb.and(
                cb.equal(scopedIdentifier.get("identifiedItem").get("item").get("id"), root.get("id")),
                cb.like(cb.lower(scopedIdentifier.get("identifier")), like)));
            return cb.or(cb.exists(sqName), cb.exists(sqIdentifier));
        };
    }

    private ConceptResponse toResponse(ObjectClass entity) {
        return new ConceptResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                entity.getSourceUri(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }
}
