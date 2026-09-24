package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.ConstraintSet;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
import io.github.matrixlane.metadataregistry.web.dto.registration.ApprovalResultResponse;
import io.github.matrixlane.metadataregistry.web.dto.registration.AvailableTransitionView;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ConstraintSetRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdministeredItemService {

    private final AdministeredItemRepository repository;
    private final RegistrationProcessService registrationProcessService;
    private final ReferenceDocumentRepository referenceDocumentRepository;
    private final ConstraintSetRepository constraintSetRepository;

    @Transactional(readOnly = true)
    public Page<AdministeredItemResponse> list(RegistrationStatus status, Pageable pageable) {
        Specification<AdministeredItem> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((root, q, cb) ->
                    cb.equal(root.get("registrationState").get("registrationStatus"), status));
        }
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AdministeredItemResponse get(UUID id) {
        AdministeredItem ai = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AdministeredItem", id));
        return toResponse(ai);
    }

    /**
     * 推进流程：该项当前状态的可达迁移动作视图（registration-process-designer）。
     */
    public List<AvailableTransitionView> availableTransitions(UUID id, UUID accountId) {
        return registrationProcessService.availableTransitions(id, accountId);
    }

    /**
     * 推进流程：直接执行无需审核的迁移；生效后返回最新注册项视图。
     */
    public AdministeredItemResponse executeTransition(UUID id, RegistrationStatus to, String note,
                                                      UUID accountId) {
        return toResponse(registrationProcessService.execute(id, to, note, accountId));
    }

    /**
     * 推进流程：批准需审核的迁移；达 quorum 自动生效，返回生效标记与最新迁移动作视图。
     */
    public ApprovalResultResponse approveTransition(UUID id, RegistrationStatus to, UUID accountId) {
        return registrationProcessService.approve(id, to, accountId);
    }

    /**
     * 推进流程：驳回进行中的 RECORDED→QUALIFIED 申请。
     */
    public AdministeredItemResponse rejectTransition(UUID id, String note, UUID accountId) {
        return toResponse(registrationProcessService.reject(id, note, accountId));
    }

    /**
     * 主管申请合格：置 PROVISIONALLY_QUALIFIED 并启动 RECORDED→QUALIFIED 审核流。
     */
    public AdministeredItemResponse requestQualification(UUID id, UUID accountId) {
        return toResponse(registrationProcessService.requestQualification(id, accountId));
    }

    public AdministeredItemResponse updateConstraintSet(UUID id, UUID constraintSetId) {
        AdministeredItem ai = loadOrThrow(id);
        ConstraintSet cs = null;
        if (constraintSetId != null) {
            cs = constraintSetRepository.findById(constraintSetId)
                    .orElseThrow(() -> new ResourceNotFoundException("ConstraintSet", constraintSetId));
        }
        ai.setConstraintSet(cs);
        return toResponse(repository.save(ai));
    }

    public AdministeredItemResponse addReferenceDocuments(UUID id, List<UUID> referenceDocumentIds) {
        AdministeredItem ai = loadOrThrow(id);
        if (referenceDocumentIds != null) {
            for (UUID docId : referenceDocumentIds) {
                ReferenceDocument doc = referenceDocumentRepository.findById(docId)
                        .orElseThrow(() -> new ResourceNotFoundException("ReferenceDocument", docId));
                if (!ai.getReferenceDocuments().contains(doc)) {
                    ai.getReferenceDocuments().add(doc);
                }
            }
        }
        return toResponse(repository.save(ai));
    }

    public AdministeredItemResponse removeReferenceDocuments(UUID id, List<UUID> referenceDocumentIds) {
        AdministeredItem ai = loadOrThrow(id);
        if (referenceDocumentIds != null && ai.getReferenceDocuments() != null) {
            ai.getReferenceDocuments().removeIf(doc -> referenceDocumentIds.contains(doc.getId()));
        }
        return toResponse(repository.save(ai));
    }

    private AdministeredItem loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AdministeredItem", id));
    }

    private AdministeredItemResponse toResponse(AdministeredItem ai) {
        Item item = ai.getItem();
        Item realItem = item == null ? null : (Item) Hibernate.unproxy(item);
        UUID itemId = realItem == null ? null : realItem.getId();
        String itemType = realItem == null ? null : realItem.getClass().getSimpleName();
        String itemLabel = realItem == null ? null : ItemNamingHelper.preferredDesignation(realItem);

        String identifier = null;
        String version = null;
        UUID nsId = null;
        String nsLabel = null;
        if (ai.getScopedIdentifiers() != null && !ai.getScopedIdentifiers().isEmpty()) {
            ScopedIdentifier si = ai.getScopedIdentifiers().get(0);
            identifier = si.getIdentifier();
            version = si.getVersion();
            if (si.getNamespace() != null) {
                nsId = si.getNamespace().getId();
                nsLabel = ItemNamingHelper.preferredDesignation(si.getNamespace());
            }
        }

        UUID raId = null;
        String raLabel = null;
        RegistrationAuthority ra = ai.getRegistrationAuthority();
        if (ra != null) {
            raId = ra.getId();
            if (ra.getName() != null && !ra.getName().isEmpty()) {
                Sign s = ra.getName().get(0);
                if (s != null) raLabel = s.getValue();
            }
        }

        UUID constraintSetId = null;
        String constraintSetLabel = null;
        if (ai.getConstraintSet() != null) {
            constraintSetId = ai.getConstraintSet().getId();
            constraintSetLabel = ItemNamingHelper.preferredDesignation(ai.getConstraintSet());
        }
        List<UUID> referenceDocumentIds = ai.getReferenceDocuments() == null
                ? List.of()
                : ai.getReferenceDocuments().stream()
                .map(ReferenceDocument::getId)
                .collect(Collectors.toList());

        RegistrationState state = ai.getRegistrationState();
        return new AdministeredItemResponse(
                ai.getId(),
                itemId,
                itemType,
                itemLabel,
                identifier,
                version,
                nsId,
                nsLabel,
                raId,
                raLabel,
                state == null ? null : state.getRegistrationStatus(),
                state == null ? null : state.getEffectiveDate(),
                state == null ? null : state.getAdministrativeNote(),
                constraintSetId,
                constraintSetLabel,
                referenceDocumentIds,
                null,
                ai.getCreationDateTime(),
                ai.getLastChangeDateTime(),
                ai.getChangeDescription(),
                null);
    }
}
