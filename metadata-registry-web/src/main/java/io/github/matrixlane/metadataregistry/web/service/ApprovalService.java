package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalRecord;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalResult;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationType;
import io.github.matrixlane.metadataregistry.web.notification.event.NotificationEvent;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ApprovalRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 1级审批工作流服务。
 *
 * <p>管理元数据项的基础审批流程：</p>
 * <ul>
 *   <li>提交审批：创建 PENDING 记录</li>
 *   <li>审批通过：标记 APPROVED，自动触发 ISO 状态迁移至 STANDARD</li>
 *   <li>审批退回：标记 REJECTED，保留审批意见</li>
 *   <li>重新提交：上次退回后可创建新的 PENDING 记录</li>
 * </ul>
 *
 * <p><b>状态迁移策略：</b></p>
 * <p>审批通过后，自动沿 ISO 11179 状态机正向迁移至 STANDARD：
 * INCOMPLETE → SUBMITTED → RECORDED → QUALIFIED → STANDARD。</p>
 *
 * <p><b>并发安全：</b></p>
 * <p>基于 {@code @Version} 乐观锁防止多人同时审批同一记录（R2）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalService {

    private final ApprovalRecordRepository approvalRecordRepository;
    private final AdministeredItemRepository administeredItemRepository;
    private final RegistrationLifecycleService registrationLifecycleService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.approval.default-approver:admin}")
    private String defaultApprover;

    private static final int MAX_OPINION_LENGTH = 2000;

    /**
     * 提交元数据项进行审批。
     *
     * @param administeredItemId 受管项 ID
     * @return 创建的审批记录
     * @throws BusinessException 管理项不存在（1104）、已存在待审批记录（1202）
     */
    public ApprovalRecord submitForApproval(UUID administeredItemId) {
        validateItemExists(administeredItemId);

        if (approvalRecordRepository.existsByAdministeredItemIdAndResult(
                administeredItemId, ApprovalResult.PENDING)) {
            throw new BusinessException(ApiCodes.APPROVAL_ALREADY_PENDING,
                    "该管理项已存在待审批记录，请勿重复提交");
        }

        String submitter = currentUsername();
        ApprovalRecord record = ApprovalRecord.ofPending(administeredItemId, submitter);
        record = approvalRecordRepository.save(record);

        // 发布通知事件给审批人（异步，事务提交后触发）
        publishNotification(defaultApprover, NotificationType.APPROVAL_SUBMITTED,
                administeredItemId, submitter, null);

        return record;
    }

    /**
     * 审批通过。
     *
     * @param approvalRecordId 审批记录 ID
     * @param opinion          审批意见（可选，最长 2000 字符）
     * @return 更新后的审批记录
     * @throws BusinessException 记录不存在（1200）、非待审批状态（1203）、意见超长（1204）、
     *                           当前 ISO 状态不允许审批通过（1201）
     */
    public ApprovalRecord approve(UUID approvalRecordId, String opinion) {
        validateOpinionLength(opinion);

        ApprovalRecord record = approvalRecordRepository.findById(approvalRecordId)
                .orElseThrow(() -> new BusinessException(ApiCodes.APPROVAL_RECORD_NOT_FOUND,
                        "审批记录不存在: " + approvalRecordId));

        if (record.getResult() != ApprovalResult.PENDING) {
            throw new BusinessException(ApiCodes.APPROVAL_NOT_PENDING,
                    "该审批记录当前状态为 " + record.getResult() + "，无法执行通过操作");
        }

        String approver = currentUsername();
        record.approve(approver, opinion);

        // 触发 ISO 状态迁移至 STANDARD
        transitionToStandard(record.getAdministeredItemId(), buildTransitionNote(approver, opinion));

        ApprovalRecord saved = approvalRecordRepository.save(record);

        // 发布通知事件给提交人（异步，事务提交后触发）
        publishNotification(record.getSubmitter(), NotificationType.APPROVAL_APPROVED,
                record.getAdministeredItemId(), record.getSubmitter(), opinion);

        return saved;
    }

    /**
     * 审批退回。
     *
     * @param approvalRecordId 审批记录 ID
     * @param opinion          退回意见（最长 2000 字符）
     * @return 更新后的审批记录
     * @throws BusinessException 记录不存在（1200）、非待审批状态（1203）、意见超长（1204）
     */
    public ApprovalRecord reject(UUID approvalRecordId, String opinion) {
        if (opinion == null || opinion.isBlank()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "退回意见不能为空");
        }
        validateOpinionLength(opinion);

        ApprovalRecord record = approvalRecordRepository.findById(approvalRecordId)
                .orElseThrow(() -> new BusinessException(ApiCodes.APPROVAL_RECORD_NOT_FOUND,
                        "审批记录不存在: " + approvalRecordId));

        if (record.getResult() != ApprovalResult.PENDING) {
            throw new BusinessException(ApiCodes.APPROVAL_NOT_PENDING,
                    "该审批记录当前状态为 " + record.getResult() + "，无法执行退回操作");
        }

        String approver = currentUsername();
        record.reject(approver, opinion);

        ApprovalRecord saved = approvalRecordRepository.save(record);

        // 发布通知事件给提交人（异步，事务提交后触发）
        publishNotification(record.getSubmitter(), NotificationType.APPROVAL_REJECTED,
                record.getAdministeredItemId(), record.getSubmitter(), opinion);

        return saved;
    }

    /**
     * 退回后重新提交审批。
     *
     * @param administeredItemId 受管项 ID
     * @return 创建的新审批记录
     * @throws BusinessException 管理项不存在（1104）、上次审批非退回状态（1205）
     */
    public ApprovalRecord resubmit(UUID administeredItemId) {
        validateItemExists(administeredItemId);

        Optional<ApprovalRecord> lastRecord = approvalRecordRepository
                .findFirstByAdministeredItemIdAndResultOrderBySubmittedAtDesc(
                        administeredItemId, ApprovalResult.REJECTED);

        if (lastRecord.isEmpty()) {
            throw new BusinessException(ApiCodes.APPROVAL_NOT_REJECTED,
                    "该管理项没有已退回的审批记录，无法重新提交");
        }

        // 检查是否已有待审批记录
        if (approvalRecordRepository.existsByAdministeredItemIdAndResult(
                administeredItemId, ApprovalResult.PENDING)) {
            throw new BusinessException(ApiCodes.APPROVAL_ALREADY_PENDING,
                    "该管理项已存在待审批记录，请勿重复提交");
        }

        String submitter = currentUsername();
        ApprovalRecord record = ApprovalRecord.ofPending(administeredItemId, submitter);
        record = approvalRecordRepository.save(record);

        // 发布通知事件给审批人（异步，事务提交后触发）
        publishNotification(defaultApprover, NotificationType.APPROVAL_SUBMITTED,
                administeredItemId, submitter, null);

        return record;
    }

    /**
     * 查询审批历史。
     *
     * @param administeredItemId 受管项 ID
     * @return 按提交时间倒序排列的审批记录列表
     */
    @Transactional(readOnly = true)
    public List<ApprovalRecord> getApprovalHistory(UUID administeredItemId) {
        return approvalRecordRepository.findByAdministeredItemIdOrderBySubmittedAtDesc(administeredItemId);
    }

    /**
     * 查询当前待审批记录。
     *
     * @param administeredItemId 受管项 ID
     * @return 待审批记录（如有）
     */
    @Transactional(readOnly = true)
    public Optional<ApprovalRecord> getPendingApproval(UUID administeredItemId) {
        return approvalRecordRepository
                .findFirstByAdministeredItemIdAndResultOrderBySubmittedAtDesc(
                        administeredItemId, ApprovalResult.PENDING);
    }

    // =========================================================================
    // 内部辅助方法
    // =========================================================================

    private void validateItemExists(UUID administeredItemId) {
        if (!administeredItemRepository.existsById(administeredItemId)) {
            throw new BusinessException(ApiCodes.ADMINISTERED_ITEM_NOT_FOUND,
                    "管理项不存在: " + administeredItemId);
        }
    }

    private void validateOpinionLength(String opinion) {
        if (opinion != null && opinion.length() > MAX_OPINION_LENGTH) {
            throw new BusinessException(ApiCodes.APPROVAL_OPINION_TOO_LONG,
                    "审批意见超过最大长度 " + MAX_OPINION_LENGTH + " 字符");
        }
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "anonymous";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser user) {
            return user.username();
        }
        return auth.getName();
    }

    private String buildTransitionNote(String approver, String opinion) {
        if (opinion == null || opinion.isBlank()) {
            return "审批通过 by " + approver;
        }
        return "审批通过 by " + approver + ": " + opinion;
    }

    /**
     * 自动触发 ISO 状态迁移，沿正向路径直至 STANDARD。
     *
     * <p>支持路径：INCOMPLETE → SUBMITTED → RECORDED → QUALIFIED → STANDARD。
     * PREFERRED 状态跳过（已是发布状态）。</p>
     */
    private void transitionToStandard(UUID administeredItemId, String note) {
        AdministeredItem item = administeredItemRepository.findById(administeredItemId)
                .orElseThrow(() -> new BusinessException(ApiCodes.ADMINISTERED_ITEM_NOT_FOUND,
                        "管理项不存在: " + administeredItemId));

        RegistrationStatus current = item.getRegistrationState() == null
                ? RegistrationStatus.INCOMPLETE
                : item.getRegistrationState().getRegistrationStatus();

        if (current == RegistrationStatus.STANDARD || current == RegistrationStatus.PREFERRED) {
            return; // 已是发布状态，无需迁移
        }

        if (current == RegistrationStatus.SUPERSEDED
                || current == RegistrationStatus.RETIRED
                || current == RegistrationStatus.HISTORICAL) {
            throw new BusinessException(ApiCodes.APPROVAL_INVALID_STATE,
                    "当前状态 " + current + " 不允许审批通过");
        }

        while (current != RegistrationStatus.STANDARD) {
            RegistrationStatus next = resolveNextState(current);
            registrationLifecycleService.transition(administeredItemId, next, note);
            current = next;
        }
    }

    private RegistrationStatus resolveNextState(RegistrationStatus current) {
        return switch (current) {
            case INCOMPLETE -> RegistrationStatus.SUBMITTED;
            case SUBMITTED -> RegistrationStatus.RECORDED;
            case RECORDED -> RegistrationStatus.QUALIFIED;
            case QUALIFIED -> RegistrationStatus.STANDARD;
            default -> throw new BusinessException(ApiCodes.APPROVAL_INVALID_STATE,
                    "无法从状态 " + current + " 自动迁移到 STANDARD");
        };
    }

    /**
     * 发布审批通知事件。
     * <p>通过 Spring Event 异步发布，在事务提交后（AFTER_COMMIT）触发，不阻塞审批主流程。</p>
     */
    private void publishNotification(String recipientId, NotificationType notificationType,
                                     UUID administeredItemId, String submitterName, String opinion) {
        try {
            String itemName = resolveItemName(administeredItemId);
            String approverName = currentUsername();

            Map<String, Object> variables = new HashMap<>();
            variables.put("itemName", itemName);
            variables.put("submitterName", submitterName);
            variables.put("approverName", approverName);
            variables.put("opinion", opinion != null ? opinion : "");

            NotificationEvent event = new NotificationEvent(
                    recipientId, notificationType, variables, administeredItemId.toString());
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("发布审批通知事件失败: {}", e.getMessage());
        }
    }

    /**
     * 解析受管项的名称（取自第一个 Designation，失败时回退为 ID）。
     */
    private String resolveItemName(UUID administeredItemId) {
        try {
            return administeredItemRepository.findById(administeredItemId)
                    .map(item -> {
                        Item coreItem = item.getItem();
                        if (coreItem != null && !coreItem.getDesignations().isEmpty()) {
                            return coreItem.getDesignations().get(0).toString();
                        }
                        return null;
                    })
                    .orElse(administeredItemId.toString());
        } catch (Exception e) {
            return administeredItemId.toString();
        }
    }
}
