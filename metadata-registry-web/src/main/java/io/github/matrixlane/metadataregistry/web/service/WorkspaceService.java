package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationTransitionApproval;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationTransitionRule;
import io.github.matrixlane.metadataregistry.ext.basicandcore.ContactAccountBinding;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionInfo;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceItemFlags;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceItemResponse;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceItemView;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSubmitRequest;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSubmitResult;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSummaryResponse;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.repository.IdentifiedItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionApprovalRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionRuleRepository;
import io.github.matrixlane.metadataregistry.web.repository.VersionHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 工作空间服务：三视图查询、行级操作标志、批量提交、请求成为候选、回退。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WorkspaceService {

    private static final Set<RegistrationStatus> ROLLBACKABLE_REGISTRATION_STATUSES = Set.of(
            // ISO/IEC 11179-6:2023 4.3.3.1.3：Candidate 起即受版本控制，故回退窗口按标准字面取 Candidate 起
            RegistrationStatus.CANDIDATE,
            RegistrationStatus.RECORDED,
            RegistrationStatus.QUALIFIED,
            RegistrationStatus.STANDARD,
            RegistrationStatus.PREFERRED_STANDARD,
            RegistrationStatus.SUPERSEDED,
            RegistrationStatus.RETIRED);

    private final AdministeredItemRepository administeredItemRepository;
    private final VersionHistoryRepository versionHistoryRepository;
    private final RegistrationTransitionApprovalRepository approvalRepository;
    private final RegistrationTransitionRuleRepository ruleRepository;
    private final ActiveRegistrarService activeRegistrarService;
    private final ItemAdministrationService itemAdministrationService;
    private final AdministrativeStatusService administrativeStatusService;
    private final RegistrationProcessService registrationProcessService;
    private final WorkspaceRollbackService workspaceRollbackService;
    private final ContactAccountBindingService contactAccountBindingService;
    private final ContactRepository contactRepository;
    private final OrganizationRepository organizationRepository;
    private final DesignationRepository designationRepository;
    private final IdentifiedItemRepository identifiedItemRepository;

    // =========================================================================
    // 三视图汇总
    // =========================================================================

    @Transactional(readOnly = true)
    public WorkspaceSummaryResponse getSummary(UUID accountId) {
        List<UUID> contactIds = contactIdsOfAccount(accountId);
        long drafts = contactIds.isEmpty() ? 0L
                : administeredItemRepository.countByAdministrativeStatusAndOwnerContacts(
                        AdministrativeStatus.DRAFT, contactIds);
        long rejected = contactIds.isEmpty() ? 0L
                : administeredItemRepository.countByAdministrativeStatusAndOwnerContacts(
                        AdministrativeStatus.REJECTED, contactIds);
        long pending = 0L;
        Optional<UUID> activeRaId = activeRegistrarAuthorityId(accountId);
        if (activeRaId.isPresent()) {
            pending = administeredItemRepository.countPendingByRegistrationAuthority(
                    activeRaId.get(), AdministrativeStatus.PROVISIONALLY_QUALIFIED);
        }
        return new WorkspaceSummaryResponse(drafts, pending, rejected);
    }

    // =========================================================================
    // 三视图分页列表
    // =========================================================================

    @Transactional(readOnly = true)
    public PageResponse<WorkspaceItemResponse> listItems(WorkspaceItemView view, Pageable pageable, UUID accountId) {
        Page<AdministeredItem> page = switch (view) {
            case DRAFTS -> findDrafts(pageable, accountId);
            case REJECTED -> findRejected(pageable, accountId);
            case PENDING -> findPending(pageable, accountId);
        };
        return PageResponse.of(page, ai -> toResponse(ai, view, accountId));
    }

    private Page<AdministeredItem> findDrafts(Pageable pageable, UUID accountId) {
        List<UUID> contactIds = contactIdsOfAccount(accountId);
        if (contactIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return administeredItemRepository.findByAdministrativeStatusAndOwnerContacts(
                AdministrativeStatus.DRAFT, contactIds, pageable);
    }

    private Page<AdministeredItem> findRejected(Pageable pageable, UUID accountId) {
        List<UUID> contactIds = contactIdsOfAccount(accountId);
        if (contactIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return administeredItemRepository.findByAdministrativeStatusAndOwnerContacts(
                AdministrativeStatus.REJECTED, contactIds, pageable);
    }

    private Page<AdministeredItem> findPending(Pageable pageable, UUID accountId) {
        Optional<UUID> activeRaId = activeRegistrarAuthorityId(accountId);
        if (activeRaId.isEmpty()) {
            return Page.empty(pageable);
        }
        return administeredItemRepository.findPendingByRegistrationAuthority(
                activeRaId.get(), AdministrativeStatus.PROVISIONALLY_QUALIFIED, pageable);
    }

    // =========================================================================
    // 批量提交
    // =========================================================================

    @Transactional
    public List<WorkspaceSubmitResult> submit(WorkspaceSubmitRequest request, UUID accountId) {
        if (request == null || request.itemIds() == null || request.itemIds().isEmpty()) {
            return List.of();
        }
        validateSubmissionInfo(request.submission(), accountId);

        List<WorkspaceSubmitResult> results = new ArrayList<>(request.itemIds().size());
        for (UUID itemId : request.itemIds()) {
            results.add(submitOne(itemId, request.submission(), accountId));
        }

        // align-submission-record-to-iso：批量提交整批创建 1 条 SubmissionRecord（USER_SUBMISSION），
        // 仅挂在本次成功提交的 AdministeredItem 上；失败的 item 不挂 SR。
        List<Item> succeededItems = new ArrayList<>();
        for (int i = 0; i < request.itemIds().size(); i++) {
            if (results.get(i).success()) {
                AdministeredItem ai = administeredItemRepository.findByItem_Id(request.itemIds().get(i))
                        .orElse(null);
                if (ai != null && ai.getItem() != null) {
                    succeededItems.add(ai.getItem());
                }
            }
        }
        if (!succeededItems.isEmpty()) {
            itemAdministrationService.recordSubmissionForBatch(
                    succeededItems, request.submission());
        }
        return results;
    }

    private WorkspaceSubmitResult submitOne(UUID itemId, SubmissionInfo submission, UUID accountId) {
        AdministeredItem ai = loadAdministeredItem(itemId);
        if (ai.getRegistrationState() == null
                || ai.getRegistrationState().getAdministrativeStatus() != AdministrativeStatus.DRAFT) {
            return new WorkspaceSubmitResult(itemId, false, ApiCodes.WORKSPACE_ITEM_NOT_DRAFT,
                    "仅管理状态为草稿的管理项可提交");
        }
        if (!itemAdministrationService.isItemOwnedByAccount(ai, accountId)) {
            return new WorkspaceSubmitResult(itemId, false, ApiCodes.SUBMITTER_OWNERSHIP_REQUIRED,
                    "该管理项不属于当前用户");
        }
        try {
            // align-submission-record-to-iso：单条工作空间提交不立即创建 SR，统一由 submit() 末尾按批次创建。
            administrativeStatusService.applyLanding(ai);
            administeredItemRepository.save(ai);
            return new WorkspaceSubmitResult(itemId, true, null, null);
        } catch (BusinessException e) {
            return new WorkspaceSubmitResult(itemId, false, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("提交管理项 {} 失败: {}", itemId, e.getMessage());
            return new WorkspaceSubmitResult(itemId, false, ApiCodes.BUSINESS_ERROR, e.getMessage());
        }
    }

    // =========================================================================
    // 请求成为候选
    // =========================================================================

    @Transactional
    public void requestCandidate(UUID itemId, SubmissionInfo submission, UUID accountId) {
        AdministeredItem ai = loadAdministeredItem(itemId);
        if (ai.getRegistrationState() == null
                || ai.getRegistrationState().getRegistrationStatus() != RegistrationStatus.INCOMPLETE) {
            throw new BusinessException(ApiCodes.WORKSPACE_REQUEST_CANDIDATE_INVALID_STATE,
                    "请求成为候选仅允许 INCOMPLETE 注册状态的管理项");
        }
        if (ai.getStewardshipRecord() == null) {
            throw new BusinessException(ApiCodes.CANDIDATE_MINIMUM_REQUIREMENT_NOT_MET,
                    "请求成为候选要求管理项具有主管记录");
        }
        if (!itemAdministrationService.isItemOwnedByAccount(ai, accountId)) {
            throw new BusinessException(ApiCodes.SUBMITTER_OWNERSHIP_REQUIRED,
                    "该管理项不属于当前用户");
        }
        validateSubmissionInfo(submission, accountId);
        itemAdministrationService.recordSubmission(ai.getItem(), syntheticRequest(submission));
        registrationProcessService.execute(ai.getId(), RegistrationStatus.CANDIDATE, "提交人请求成为候选", accountId);
    }

    // =========================================================================
    // 回退
    // =========================================================================

    @Transactional
    public void rollback(UUID itemId, UUID accountId) {
        AdministeredItem ai = loadAdministeredItem(itemId);
        if (!itemAdministrationService.isItemOwnedByAccount(ai, accountId)) {
            throw new BusinessException(ApiCodes.SUBMITTER_OWNERSHIP_REQUIRED,
                    "该管理项不属于当前用户");
        }
        workspaceRollbackService.rollback(itemId);
    }

    // =========================================================================
    // 视图行 DTO 与标志
    // =========================================================================

    private WorkspaceItemResponse toResponse(AdministeredItem ai, WorkspaceItemView view, UUID accountId) {
        // 瘦映射：绝不初始化 ai.getItem() 代理——Item 联合继承整图加载会超出
        // SQL Server 选择列表 4096 列上限（SQLite 64 表 join 上限）。类型经子类表探测，
        // 指称经 Designation 瘦查询，均不触碰 Item 图。
        UUID itemId = ai.getItem() == null ? null : ai.getItem().getId();
        String itemType = itemId == null ? null : typeNameOf(itemId);
        String label = itemId == null ? null : designationLabel(itemId);
        String version = firstVersion(ai);
        RegistrationStatus registrationStatus = ai.getRegistrationState() == null ? null
                : ai.getRegistrationState().getRegistrationStatus();
        AdministrativeStatus administrativeStatus = ai.getRegistrationState() == null ? null
                : ai.getRegistrationState().getAdministrativeStatus();
        String administrativeStatusValue = administrativeStatus == null ? null : administrativeStatus.standardValue();
        boolean hasRollbackBaseline = itemId != null
                && versionHistoryRepository.existsByItemIdAndChangeType(itemId, "BASELINE");
        WorkspaceItemFlags flags = computeFlags(ai, view, accountId, hasRollbackBaseline);
        return new WorkspaceItemResponse(itemId, itemType, label, registrationStatus,
                administrativeStatusValue, version, hasRollbackBaseline, flags);
    }

    private WorkspaceItemFlags computeFlags(AdministeredItem ai, WorkspaceItemView view, UUID accountId,
                                            boolean hasRollbackBaseline) {
        RegistrationStatus registrationStatus = ai.getRegistrationState() == null ? null
                : ai.getRegistrationState().getRegistrationStatus();
        AdministrativeStatus administrativeStatus = ai.getRegistrationState() == null ? null
                : ai.getRegistrationState().getAdministrativeStatus();
        boolean owned = itemAdministrationService.isItemOwnedByAccount(ai, accountId);

        boolean canSubmit = owned && administrativeStatus == AdministrativeStatus.DRAFT;
        boolean canRequestCandidate = registrationStatus == RegistrationStatus.INCOMPLETE
                && ai.getStewardshipRecord() != null
                && owned;
        boolean canRequestQualification = registrationStatus == RegistrationStatus.RECORDED
                && administrativeStatus != AdministrativeStatus.PROVISIONALLY_QUALIFIED
                && itemAdministrationService.isItemStewardedByAccount(ai, accountId);
        boolean canApprove = canApprove(ai, accountId);
        boolean canReject = canReject(ai, accountId);
        boolean canRollback = owned
                && ROLLBACKABLE_REGISTRATION_STATUSES.contains(registrationStatus)
                && (administrativeStatus == AdministrativeStatus.DRAFT
                        || administrativeStatus == AdministrativeStatus.REJECTED)
                && (hasRollbackBaseline || administrativeStatus == AdministrativeStatus.REJECTED);

        return new WorkspaceItemFlags(canSubmit, canRequestCandidate, canRequestQualification,
                canApprove, canReject, canRollback);
    }

    private boolean canApprove(AdministeredItem ai, UUID accountId) {
        if (accountId == null) {
            return false;
        }
        Optional<Registrar> active = activeRegistrarService.resolveActiveRegistrar(accountId);
        if (active.isEmpty()) {
            return false;
        }
        UUID authorityId = ai.getRegistrationAuthority() == null ? null : ai.getRegistrationAuthority().getId();
        if (authorityId == null
                || active.get().getRegistrationAuthority() == null
                || !authorityId.equals(active.get().getRegistrationAuthority().getId())) {
            return false;
        }
        registrationProcessService.ensureSeed(authorityId);
        AdministrativeStatus adminStatus = ai.getRegistrationState() == null ? null
                : ai.getRegistrationState().getAdministrativeStatus();
        if (adminStatus == AdministrativeStatus.PROVISIONALLY_QUALIFIED) {
            RegistrationTransitionRule rule = ruleRepository
                    .findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                            authorityId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED)
                    .orElse(null);
            return rule != null && rule.isRequiresApproval()
                    && RegistrationProcessService.isQualifiedApprover(active, authorityId, rule);
        }
        List<RegistrationTransitionApproval> pending = approvalRepository.findByAdministeredItemId(ai.getId());
        for (RegistrationTransitionApproval approval : pending) {
            RegistrationTransitionRule rule = ruleRepository
                    .findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                            authorityId, approval.getFromStatus(), approval.getToStatus())
                    .orElse(null);
            if (rule != null && rule.isRequiresApproval()
                    && RegistrationProcessService.isQualifiedApprover(active, authorityId, rule)) {
                return true;
            }
        }
        return false;
    }

    private boolean canReject(AdministeredItem ai, UUID accountId) {
        if (accountId == null) {
            return false;
        }
        if (ai.getRegistrationState() == null
                || ai.getRegistrationState().getAdministrativeStatus() != AdministrativeStatus.PROVISIONALLY_QUALIFIED) {
            return false;
        }
        Optional<Registrar> active = activeRegistrarService.resolveActiveRegistrar(accountId);
        if (active.isEmpty()) {
            return false;
        }
        UUID authorityId = ai.getRegistrationAuthority() == null ? null : ai.getRegistrationAuthority().getId();
        if (authorityId == null
                || active.get().getRegistrationAuthority() == null
                || !authorityId.equals(active.get().getRegistrationAuthority().getId())) {
            return false;
        }
        registrationProcessService.ensureSeed(authorityId);
        RegistrationTransitionRule rule = ruleRepository
                .findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                        authorityId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED)
                .orElse(null);
        return rule != null && rule.isRequiresApproval()
                && RegistrationProcessService.isQualifiedApprover(active, authorityId, rule);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private AdministeredItem loadAdministeredItem(UUID itemId) {
        return administeredItemRepository.findByItem_Id(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("AdministeredItem", itemId));
    }

    private List<UUID> contactIdsOfAccount(UUID accountId) {
        if (accountId == null) {
            return List.of();
        }
        return contactAccountBindingService.bindingsOfAccount(accountId).stream()
                .map(ContactAccountBinding::getContactId)
                .toList();
    }

    private Optional<UUID> activeRegistrarAuthorityId(UUID accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return activeRegistrarService.resolveActiveRegistrar(accountId)
                .filter(r -> r.getRegistrationAuthority() != null)
                .map(r -> r.getRegistrationAuthority().getId());
    }

    private String firstVersion(AdministeredItem ai) {
        if (ai.getScopedIdentifiers() == null || ai.getScopedIdentifiers().isEmpty()) {
            return null;
        }
        return ai.getScopedIdentifiers().get(0).getVersion();
    }

    /** 条目具体类型：子类表探测（只查子类表主键），不加载 Item 联合继承整图。 */
    private String typeNameOf(UUID itemId) {
        Class<?> type = identifiedItemRepository.findItemConcreteTypeById(itemId);
        return type == null ? null : type.getSimpleName();
    }

    /** 首选指称：Designation 瘦查询（语言排序同 ItemNamingHelper.pickByLanguage），不加载 Item 图。 */
    private String designationLabel(UUID itemId) {
        List<String> values = designationRepository.findSignValuesByItemIdOrdered(itemId);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * 提交信息校验（acg-submission）：与
     * {@link ItemAdministrationService#resolveContactAndOrganization} 保持同一套规则——
     * organizationId 显式给出时，必须能从当前账号绑定的身份中解析出该组织下的
     * 联系人身份；contactId 同时给出时追加从属校验（联系人须属于所选组织）。
     * 仅给 contactId 时保持历史行为（联系人必须属于当前账号）。
     */
    private void validateSubmissionInfo(SubmissionInfo submission, UUID accountId) {
        if (submission == null) {
            return;
        }
        if (submission.organizationId() != null) {
            organizationRepository.findById(submission.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", submission.organizationId()));
            resolveOwnIdentityInOrganization(accountId, submission.organizationId());
            if (submission.contactId() != null) {
                Contact contact = contactRepository.findById(submission.contactId())
                        .orElseThrow(() -> new ResourceNotFoundException("Contact", submission.contactId()));
                Optional<UUID> boundAccount = contactAccountBindingService.accountIdOfContact(contact.getId());
                if (boundAccount.isEmpty() || !accountId.equals(boundAccount.get())) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "提交信息中的联系人必须是当前账号绑定的联系人身份");
                }
                if (contact.getOrganization() == null
                        || !submission.organizationId().equals(contact.getOrganization().getId())) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "提交信息中的联系人必须属于所选提交组织");
                }
            }
            return;
        }
        if (submission.contactId() == null) {
            return;
        }
        Contact contact = contactRepository.findById(submission.contactId())
                .orElseThrow(() -> new ResourceNotFoundException("Contact", submission.contactId()));
        Optional<UUID> boundAccount = contactAccountBindingService.accountIdOfContact(contact.getId());
        if (boundAccount.isEmpty() || !accountId.equals(boundAccount.get())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "提交信息中的联系人必须是当前账号绑定的联系人身份");
        }
    }

    /**
     * 当前账号绑定的身份中，organization.id == organizationId 的联系人身份；无则空。
     * 确定性排序（minor-1）：默认绑定优先，其余按绑定顺序（contactId 升序，即最早绑定在前）——
     * findAllById 的返回顺序未定义，同一组织多身份时不得直接 findFirst。
     */
    private Contact resolveOwnIdentityInOrganization(UUID accountId, UUID organizationId) {
        List<ContactAccountBinding> bindings = contactAccountBindingService.bindingsOfAccount(accountId);
        List<UUID> contactIds = bindings.stream()
                .map(ContactAccountBinding::getContactId)
                .toList();
        if (contactIds.isEmpty()) {
            throw noIdentityInOrganization();
        }
        Set<UUID> defaultIds = bindings.stream()
                .filter(ContactAccountBinding::isDefault)
                .map(ContactAccountBinding::getContactId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, Contact> byId = new java.util.HashMap<>();
        contactRepository.findAllById(contactIds).forEach(c -> byId.put(c.getId(), c));
        return contactIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(c -> c.getOrganization() != null && organizationId.equals(c.getOrganization().getId()))
                .sorted(java.util.Comparator.comparing(c -> !defaultIds.contains(c.getId())))
                .findFirst()
                .orElseThrow(this::noIdentityInOrganization);
    }

    private BusinessException noIdentityInOrganization() {
        return new BusinessException(ApiCodes.VALIDATION_FAILED,
                "提交组织必须是当前账号具有联系人身份的组织，请先创建为该机构联系人");
    }

    private ItemRegistrationRequest syntheticRequest(SubmissionInfo submission) {
        return new ItemRegistrationRequest(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, submission, null);
    }
}
