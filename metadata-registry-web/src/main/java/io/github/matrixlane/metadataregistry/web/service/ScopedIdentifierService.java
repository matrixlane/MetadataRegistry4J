package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.ext.identification.IdentifierCreationMode;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierVersionHistory;
import io.github.matrixlane.metadataregistry.ext.identification.ScopedIdentifierLifecycle;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.ext.identification.VersionStatus;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTemplateException;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SequenceOverflowException;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.IdentifierVersionHistoryResponse;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierCreateRequest;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierResponse;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.VersionTransitionRequest;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierVersionHistoryRepository;
import io.github.matrixlane.metadataregistry.web.repository.IdentifiedItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.service.identifier.IdentifierInputPolicyService;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import io.github.matrixlane.metadataregistry.web.service.namespace.NamespaceConstraintValidator;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ScopedIdentifierService {

    private final ScopedIdentifierRepository scopedIdentifierRepository;
    private final IdentifierVersionHistoryRepository historyRepository;
    private final NamespaceRepository namespaceRepository;
    private final IdentifiedItemRepository identifiedItemRepository;
    private final IdentifierInputPolicyService identifierInputPolicyService;
    private final NamespaceConstraintValidator namespaceConstraintValidator;
    private final ScopedIdentifierLifecycleService lifecycleService;

    @Transactional(readOnly = true)
    public Page<ScopedIdentifierResponse> list(Pageable pageable) {
        return scopedIdentifierRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ScopedIdentifierResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ScopedIdentifierResponse create(ScopedIdentifierCreateRequest request) {
        Namespace namespace = namespaceRepository.findById(request.namespaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Namespace", request.namespaceId()));

        if (!identifiedItemRepository.existsById(request.identifiedItemId())) {
            throw new ResourceNotFoundException("IdentifiedItem", request.identifiedItemId());
        }
        // 只为段模板 {type} 解析取持久化类名：先读 FK，再按 type() 探测具体类
        // （只探测子类表主键），不加载 Item 联合子类对象图（30+ 子类整图 join
        // 超出 SQLite 单条 SQL 64 表上限）；实体本体用 getReference 代理，仅借其外键。
        Class<?> itemClass = identifiedItemRepository.findItemIdById(request.identifiedItemId())
                .map(identifiedItemRepository::findItemConcreteTypeById)
                .orElse(null);
        Class<?> identifiedItemClass = IdentifiedItem.class;
        String itemType = (itemClass != null ? itemClass : identifiedItemClass).getSimpleName();
        IdentifiedItem identifiedItem = identifiedItemRepository.getReferenceById(request.identifiedItemId());
        boolean autoGenerate = request.identifier() == null || request.identifier().isBlank();
        // 自动生成的标识（尤其时间戳唯一性段）与已有标识撞库时有界重试：
        // 重试重新执行生成（时间戳取新值）；顺序号有悲观锁、UUID 碰撞概率可忽略，
        // 对二者重试同样无害。手动标识不重试，直接报错。
        int maxAttempts = autoGenerate ? 3 : 1;
        for (int attempt = 1; ; attempt++) {
            String identifier;
            try {
                identifier = identifierInputPolicyService.resolveIdentifier(
                        namespace, request.identifier(), itemType, request.variables());
            } catch (SequenceOverflowException e) {
                throw new BusinessException("序号溢出：" + e.getMessage());
            } catch (SegmentTemplateException e) {
                throw new BusinessException("标识生成失败：" + e.getMessage());
            }
            String version = identifierInputPolicyService.resolveVersion(namespace, request.version());

            if (scopedIdentifierRepository.existsByNamespaceIdAndIdentifier(namespace.getId(), identifier)) {
                if (autoGenerate && attempt < maxAttempts) {
                    continue;
                }
                throw new BusinessException("Identifier already exists in namespace: " + identifier);
            }

            ScopedIdentifier entity = new ScopedIdentifier();
            entity.setIdentifier(identifier);
            entity.setVersion(version);
            entity.setNamespace(namespace);
            entity.setIdentifiedItem(identifiedItem);
            // 生命周期扩展属性（创建方式/版本状态/时间戳）写入卫星实体（ext）
            ScopedIdentifierLifecycle lifecycle = new ScopedIdentifierLifecycle();
            lifecycle.setVersionStatus(VersionStatus.DRAFT);
            lifecycle.setCreatedAt(LocalDateTime.now());
            // 持久化创建选择（AUTO/MANUAL）：外部 SI 始终 MANUAL，null；自有名下按输入推导。
            lifecycle.setIdentifierCreationMode(autoGenerate
                    ? IdentifierCreationMode.AUTO
                    : IdentifierCreationMode.MANUAL);
            lifecycle.setVersionCreationMode((request.version() == null || request.version().isBlank())
                    ? IdentifierCreationMode.AUTO
                    : IdentifierCreationMode.MANUAL);

            namespaceConstraintValidator.validateScopedIdentifier(namespace.getId(), entity, null);

            try {
                ScopedIdentifier saved = scopedIdentifierRepository.save(entity);
                lifecycleService.stageNew(saved, lifecycle);
                recordHistory(saved, version, VersionStatus.DRAFT, "Created: " + (request.changeSummary() != null ? request.changeSummary() : "Initial version"));
                return toCreatedResponse(saved, identifiedItemClass, lifecycle);
            } catch (DataIntegrityViolationException e) {
                if (autoGenerate && attempt < maxAttempts) {
                    continue;
                }
                throw new BusinessException("Identifier already exists in namespace (concurrent conflict): " + identifier);
            }
        }
    }

    public ScopedIdentifierResponse update(UUID id, ScopedIdentifierCreateRequest request) {
        ScopedIdentifier entity = loadOrThrow(id);
        ScopedIdentifierLifecycle lifecycle = lifecycleService.getOrDefault(id);

        // identifier 变更遵循 SI 持久化的 creation_mode：AUTO 拒绝；MANUAL / NULL 接受
        if (request.identifier() != null && !request.identifier().isBlank()
                && !request.identifier().equals(entity.getIdentifier())) {
            if (lifecycle.getIdentifierCreationMode() == IdentifierCreationMode.AUTO) {
                throw new BusinessException("该标识符创建时选择自动生成，不允许手动修改");
            }
            entity.setIdentifier(request.identifier());
        }

        // 版本变更遵循 creation_mode：AUTO 忽略显式值；MANUAL / NULL 接受并校验
        if (request.version() != null && !request.version().isBlank()
                && !request.version().equals(entity.getVersion())) {
            if (lifecycle.getVersionCreationMode() == IdentifierCreationMode.AUTO) {
                // 编辑 AUTO 时 MUST 忽略显式 version；保持原值不变（由调用方按策略 bump）
                // 此处不写值即可，由 ItemAdministrationService 负责按策略递增；
                // 本路径仅在 SI 独立更新接口被调用时触发。
            } else {
                identifierInputPolicyService.validateManualVersion(entity.getNamespace(), request.version());
                entity.setVersion(request.version());
            }
        }

        if (request.namespaceId() != null
                && !request.namespaceId().equals(entity.getNamespace().getId())) {
            Namespace ns = namespaceRepository.findById(request.namespaceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Namespace", request.namespaceId()));
            entity.setNamespace(ns);
        }

        if (request.identifiedItemId() != null
                && !request.identifiedItemId().equals(entity.getIdentifiedItem().getId())) {
            if (!identifiedItemRepository.existsById(request.identifiedItemId())) {
                throw new ResourceNotFoundException("IdentifiedItem", request.identifiedItemId());
            }
            // 仅需更换外键引用：getReference 代理即可，避免加载 Item 联合子类整图
            entity.setIdentifiedItem(identifiedItemRepository.getReferenceById(request.identifiedItemId()));
        }

        namespaceConstraintValidator.validateScopedIdentifier(
                entity.getNamespace().getId(), entity, entity.getIdentifiedItem().getId());

        ScopedIdentifier saved = scopedIdentifierRepository.save(entity);
        return toResponse(saved);
    }

    public ScopedIdentifierResponse transition(UUID id, VersionTransitionRequest request) {
        ScopedIdentifier entity = loadOrThrow(id);
        VersionStatus current = lifecycleService.getOrDefault(id).getVersionStatus();
        VersionStatus target = request.toStatus();

        validateTransition(current, target);

        LocalDateTime now = LocalDateTime.now();
        lifecycleService.mutate(id, lc -> {
            lc.setVersionStatus(target);
            switch (target) {
                case PUBLISHED -> lc.setPublishedAt(now);
                case DEPRECATED -> lc.setDeprecatedAt(now);
                case OBSOLETE -> lc.setObsoleteAt(now);
                default -> { /* no-op */ }
            }
        });

        ScopedIdentifier saved = scopedIdentifierRepository.save(entity);
        recordHistory(saved, saved.getVersion(), target, request.changeSummary());
        return toResponse(saved);
    }

    public void delete(UUID id) {
        if (!scopedIdentifierRepository.existsById(id)) {
            throw new ResourceNotFoundException("ScopedIdentifier", id);
        }
        scopedIdentifierRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<IdentifierVersionHistoryResponse> listHistory(UUID scopedIdentifierId) {
        return historyRepository.findByScopedIdentifierIdOrderByTimestampDesc(scopedIdentifierId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private ScopedIdentifier loadOrThrow(UUID id) {
        return scopedIdentifierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScopedIdentifier", id));
    }

    private void validateTransition(VersionStatus current, VersionStatus target) {
        boolean valid = switch (current) {
            case DRAFT -> target == VersionStatus.PUBLISHED || target == VersionStatus.OBSOLETE;
            case PUBLISHED -> target == VersionStatus.DEPRECATED || target == VersionStatus.OBSOLETE;
            case DEPRECATED -> target == VersionStatus.OBSOLETE;
            case OBSOLETE -> false;
        };
        if (!valid) {
            throw new BusinessException(
                    "Invalid version transition from " + current + " to " + target);
        }
    }

    private void recordHistory(ScopedIdentifier si, String version,
                               VersionStatus status, String summary) {
        IdentifierVersionHistory history = new IdentifierVersionHistory(
                si, version, status, "system", summary);
        historyRepository.save(history);
    }

    /**
     * 创建路径专用响应：IdentifiedItem 是未初始化代理，不触发其加载
     * （Item 联合子类整图 join 在 SQLite 上超出 64 表上限）；类型取自
     * create() 已完成的 type() 探测，标签留空，详情经查询接口获取。
     */
    private ScopedIdentifierResponse toCreatedResponse(ScopedIdentifier si, Class<?> identifiedItemClass,
                                                       ScopedIdentifierLifecycle lifecycle) {
        Namespace ns = si.getNamespace();
        return new ScopedIdentifierResponse(
                si.getId(),
                ns == null ? null : ns.getId(),
                ns == null ? null : ItemNamingHelper.preferredDesignation(ns),
                si.getIdentifier(),
                si.getVersion(),
                lifecycle.getVersionStatus(),
                si.getIdentifiedItem() == null ? null : si.getIdentifiedItem().getId(),
                identifiedItemClass == null ? null : identifiedItemClass.getSimpleName(),
                null,
                lifecycle.getCreatedAt(),
                lifecycle.getPublishedAt(),
                lifecycle.getDeprecatedAt(),
                lifecycle.getObsoleteAt(),
                lifecycle.getIdentifierCreationMode(),
                lifecycle.getVersionCreationMode()
        );
    }

    private ScopedIdentifierResponse toResponse(ScopedIdentifier si) {
        Namespace ns = si.getNamespace();
        IdentifiedItem ii = si.getIdentifiedItem();
        ScopedIdentifierLifecycle lifecycle = lifecycleService.getOrDefault(si.getId());

        UUID identifiedItemId = null;
        String identifiedItemType = null;
        String identifiedItemLabel = null;
        if (ii != null) {
            identifiedItemId = ii.getId();
            Object unproxied = Hibernate.unproxy(ii);
            identifiedItemType = unproxied.getClass().getSimpleName();
            if (ii.getItem() != null) {
                identifiedItemLabel = ItemNamingHelper.preferredDesignation(ii.getItem());
            }
        }

        return new ScopedIdentifierResponse(
                si.getId(),
                ns == null ? null : ns.getId(),
                ns == null ? null : ItemNamingHelper.preferredDesignation(ns),
                si.getIdentifier(),
                si.getVersion(),
                lifecycle.getVersionStatus(),
                identifiedItemId,
                identifiedItemType,
                identifiedItemLabel,
                lifecycle.getCreatedAt(),
                lifecycle.getPublishedAt(),
                lifecycle.getDeprecatedAt(),
                lifecycle.getObsoleteAt(),
                lifecycle.getIdentifierCreationMode(),
                lifecycle.getVersionCreationMode()
        );
    }

    private IdentifierVersionHistoryResponse toHistoryResponse(IdentifierVersionHistory h) {
        return new IdentifierVersionHistoryResponse(
                h.getId(),
                h.getScopedIdentifier().getId(),
                h.getVersion(),
                h.getVersionStatus(),
                h.getOperator(),
                h.getTimestamp(),
                h.getChangeSummary()
        );
    }
}