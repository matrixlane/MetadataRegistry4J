package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalProcessInstance;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalProcessStatus;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalResult;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStep;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepAssigneeRule;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepRecord;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepStatus;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalTemplate;
import io.github.matrixlane.metadataregistry.web.entity.RejectStrategy;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ApprovalProcessInstanceRepository;
import io.github.matrixlane.metadataregistry.web.repository.ApprovalRecordRepository;
import io.github.matrixlane.metadataregistry.web.repository.ApprovalStepRecordRepository;
import io.github.matrixlane.metadataregistry.web.repository.ApprovalStepRepository;
import io.github.matrixlane.metadataregistry.web.repository.ApprovalTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 多级串行审批工作流服务。
 *
 * <p>管理基于审批流程模板的多级串行审批：</p>
 * <ul>
 *   <li>启动审批：基于模板创建流程实例和步骤记录</li>
 *   <li>步骤通过：当前步骤 APPROVED，自动推进到下一级；最后一级通过后触发 ISO 状态迁移</li>
 *   <li>步骤退回：当前步骤 REJECTED，触发整体退回（流程实例 REJECTED）</li>
 *   <li>超时转派：将当前待审批步骤转派给新的审批人</li>
 *   <li>重新提交：整体退回后，从头开始新流程</li>
 * </ul>
 *
 * <p><b>轻量工作流引擎接口契约：</b></p>
 * <ul>
 *   <li>状态枚举：{@link ApprovalProcessStatus} / {@link ApprovalStepStatus}</li>
 *   <li>事件类型：APPROVE_STEP / REJECT_STEP / TRANSFER_STEP / START_PROCESS / RESUBMIT</li>
 *   <li>转换规则：串行推进，最后一步触发 ISO 迁移</li>
 * </ul>
 *
 * <p><b>MVP 边界：</b></p>
 * <ul>
 *   <li>支持 2 级串行审批（核心）</li>
 *   <li>审批人分配：按人（BY_USER）+ 按角色（BY_ROLE）</li>
 *   <li>退回策略：整体退回（REJECT_ALL），表结构预留逐级退回（REJECT_TO_PREV）</li>
 *   <li>超时阈值：24h / 72h / 7d</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MultiLevelApprovalService {

    private final ApprovalTemplateRepository approvalTemplateRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final ApprovalProcessInstanceRepository approvalProcessInstanceRepository;
    private final ApprovalStepRecordRepository approvalStepRecordRepository;
    private final AdministeredItemRepository administeredItemRepository;
    private final RegistrationLifecycleService registrationLifecycleService;
    private final ApprovalRecordRepository approvalRecordRepository;

    private static final int MAX_OPINION_LENGTH = 2000;

    // =========================================================================
    // 1. 流程生命周期
    // =========================================================================

    /**
     * 启动多级审批流程。
     *
     * @param administeredItemId 受管项 ID
     * @param templateId         审批模板 ID
     * @return 创建的流程实例
     * @throws BusinessException 管理项不存在、模板不存在/未启用、已有进行中的审批
     */
    public ApprovalProcessInstance startApproval(UUID administeredItemId, UUID templateId) {
        validateItemExists(administeredItemId);

        ApprovalTemplate template = approvalTemplateRepository.findByIdAndEnabledTrue(templateId)
                .orElseThrow(() -> new BusinessException(ApiCodes.APPROVAL_TEMPLATE_NOT_FOUND,
                        "审批模板不存在或未启用: " + templateId));

        // 检查是否已有进行中的审批
        if (approvalProcessInstanceRepository.existsByAdministeredItemIdAndStatus(
                administeredItemId, ApprovalProcessStatus.PENDING)) {
            throw new BusinessException(ApiCodes.APPROVAL_PROCESS_ALREADY_PENDING,
                    "该管理项已存在进行中的多级审批流程");
        }

        // 检查是否已有 1 级待审批记录（互斥）
        if (approvalRecordRepository.existsByAdministeredItemIdAndResult(
                administeredItemId, ApprovalResult.PENDING)) {
            throw new BusinessException(ApiCodes.APPROVAL_ALREADY_PENDING,
                    "该管理项已存在待审批记录，请先处理");
        }

        List<ApprovalStep> steps = approvalStepRepository.findByTemplateIdOrderByStepNumberAsc(templateId);
        if (steps.isEmpty()) {
            throw new BusinessException(ApiCodes.APPROVAL_INVALID_TEMPLATE,
                    "审批模板未定义任何步骤: " + templateId);
        }

        String submitter = currentUsername();
        ApprovalProcessInstance instance = ApprovalProcessInstance.of(template, administeredItemId, submitter);
        ApprovalProcessInstance savedInstance = approvalProcessInstanceRepository.save(instance);

        // 创建第一步的记录
        ApprovalStep firstStep = steps.get(0);
        ApprovalStepRecord stepRecord = ApprovalStepRecord.of(
                savedInstance,
                firstStep.getStepNumber(),
                firstStep.getStepName(),
                firstStep.getAssigneeRule(),
                firstStep.getAssigneeValue(),
                resolveTimeoutHours(firstStep, template));
        approvalStepRecordRepository.save(stepRecord);

        savedInstance.getStepRecords().add(stepRecord);
        return savedInstance;
    }

    /**
     * 审批通过当前步骤。
     *
     * @param stepRecordId 步骤记录 ID
     * @param opinion      审批意见（可选，最长 2000 字符）
     * @return 更新后的步骤记录
     * @throws BusinessException 记录不存在、非待审批状态、非当前步骤、审批人无权限
     */
    public ApprovalStepRecord approveStep(UUID stepRecordId, String opinion) {
        validateOpinionLength(opinion);

        ApprovalStepRecord stepRecord = approvalStepRecordRepository.findById(stepRecordId)
                .orElseThrow(() -> new BusinessException(ApiCodes.APPROVAL_STEP_NOT_FOUND,
                        "审批步骤记录不存在: " + stepRecordId));

        ApprovalProcessInstance instance = stepRecord.getProcessInstance();

        // 验证流程状态
        if (!instance.isPending()) {
            throw new BusinessException(ApiCodes.APPROVAL_NOT_PENDING,
                    "该审批流程当前状态为 " + instance.getStatus() + "，无法执行通过操作");
        }

        // 验证是否为当前步骤
        if (!stepRecord.getStepNumber().equals(instance.getCurrentStep())) {
            throw new BusinessException(ApiCodes.APPROVAL_STEP_NOT_CURRENT,
                    "该步骤不是当前待审批步骤，当前步骤为第 " + instance.getCurrentStep() + " 步");
        }

        // 验证步骤状态
        if (stepRecord.getStatus() != ApprovalStepStatus.PENDING
                && stepRecord.getStatus() != ApprovalStepStatus.TRANSFERRED) {
            throw new BusinessException(ApiCodes.APPROVAL_STEP_NOT_PENDING,
                    "该步骤当前状态为 " + stepRecord.getStatus() + "，无法执行通过操作");
        }

        // 验证审批人权限
        String approver = currentUsername();
        validateApproverPermission(stepRecord, approver);

        stepRecord.approve(approver, opinion);
        approvalStepRecordRepository.save(stepRecord);

        // 推进流程
        List<ApprovalStep> steps = approvalStepRepository.findByTemplateIdOrderByStepNumberAsc(
                instance.getTemplate().getId());
        boolean completed = instance.advance(steps.size());
        approvalProcessInstanceRepository.save(instance);

        if (completed) {
            // 最后一步通过，触发 ISO 状态迁移
            String note = buildTransitionNote(approver, opinion);
            transitionToStandard(instance.getAdministeredItemId(), note);
        } else {
            // 创建下一步记录
            ApprovalStep nextStepDef = steps.get(instance.getCurrentStep() - 1);
            ApprovalStepRecord nextRecord = ApprovalStepRecord.of(
                    instance,
                    nextStepDef.getStepNumber(),
                    nextStepDef.getStepName(),
                    nextStepDef.getAssigneeRule(),
                    nextStepDef.getAssigneeValue(),
                    resolveTimeoutHours(nextStepDef, instance.getTemplate()));
            approvalStepRecordRepository.save(nextRecord);
            instance.getStepRecords().add(nextRecord);
        }

        return stepRecord;
    }

    /**
     * 审批退回当前步骤（整体退回）。
     *
     * @param stepRecordId 步骤记录 ID
     * @param opinion      退回意见（必填，最长 2000 字符）
     * @return 更新后的步骤记录
     * @throws BusinessException 记录不存在、非待审批状态、非当前步骤、意见为空
     */
    public ApprovalStepRecord rejectStep(UUID stepRecordId, String opinion) {
        if (opinion == null || opinion.isBlank()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "退回意见不能为空");
        }
        validateOpinionLength(opinion);

        ApprovalStepRecord stepRecord = approvalStepRecordRepository.findById(stepRecordId)
                .orElseThrow(() -> new BusinessException(ApiCodes.APPROVAL_STEP_NOT_FOUND,
                        "审批步骤记录不存在: " + stepRecordId));

        ApprovalProcessInstance instance = stepRecord.getProcessInstance();

        if (!instance.isPending()) {
            throw new BusinessException(ApiCodes.APPROVAL_NOT_PENDING,
                    "该审批流程当前状态为 " + instance.getStatus() + "，无法执行退回操作");
        }

        if (!stepRecord.getStepNumber().equals(instance.getCurrentStep())) {
            throw new BusinessException(ApiCodes.APPROVAL_STEP_NOT_CURRENT,
                    "该步骤不是当前待审批步骤，当前步骤为第 " + instance.getCurrentStep() + " 步");
        }

        if (stepRecord.getStatus() != ApprovalStepStatus.PENDING
                && stepRecord.getStatus() != ApprovalStepStatus.TRANSFERRED) {
            throw new BusinessException(ApiCodes.APPROVAL_STEP_NOT_PENDING,
                    "该步骤当前状态为 " + stepRecord.getStatus() + "，无法执行退回操作");
        }

        String approver = currentUsername();
        validateApproverPermission(stepRecord, approver);

        // 标记步骤为退回
        stepRecord.reject(approver, opinion);
        approvalStepRecordRepository.save(stepRecord);

        // 整体退回：标记流程实例为 REJECTED
        instance.rejectAll();
        approvalProcessInstanceRepository.save(instance);

        return stepRecord;
    }

    /**
     * 退回后重新提交审批。
     *
     * @param administeredItemId 受管项 ID
     * @param templateId         审批模板 ID（可选，为 null 则复用上次模板）
     * @return 创建的新流程实例
     * @throws BusinessException 管理项不存在、没有已退回的流程、已有进行中的审批
     */
    public ApprovalProcessInstance resubmit(UUID administeredItemId, UUID templateId) {
        validateItemExists(administeredItemId);

        // 查找最近一次已退回的流程实例
        Optional<ApprovalProcessInstance> lastRejected = approvalProcessInstanceRepository
                .findFirstByAdministeredItemIdAndStatusOrderByCreatedAtDesc(
                        administeredItemId, ApprovalProcessStatus.REJECTED);

        if (lastRejected.isEmpty()) {
            throw new BusinessException(ApiCodes.APPROVAL_NOT_REJECTED,
                    "该管理项没有已退回的多级审批流程，无法重新提交");
        }

        // 检查是否已有进行中的审批
        if (approvalProcessInstanceRepository.existsByAdministeredItemIdAndStatus(
                administeredItemId, ApprovalProcessStatus.PENDING)) {
            throw new BusinessException(ApiCodes.APPROVAL_PROCESS_ALREADY_PENDING,
                    "该管理项已存在进行中的多级审批流程");
        }

        UUID effectiveTemplateId = templateId != null ? templateId : lastRejected.get().getTemplate().getId();
        return startApproval(administeredItemId, effectiveTemplateId);
    }

    // =========================================================================
    // 2. 超时处理
    // =========================================================================

    /**
     * 超时转派。
     *
     * @param stepRecordId  步骤记录 ID
     * @param newAssignee   新审批人（按人分配时为用户名）
     * @param newAssigneeRole 新审批角色（按角色分配时为角色名，可选）
     * @return 更新后的步骤记录
     */
    public ApprovalStepRecord transferStep(UUID stepRecordId, String newAssignee, String newAssigneeRole) {
        if ((newAssignee == null || newAssignee.isBlank()) && (newAssigneeRole == null || newAssigneeRole.isBlank())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "新审批人或审批角色至少填写一个");
        }

        ApprovalStepRecord stepRecord = approvalStepRecordRepository.findById(stepRecordId)
                .orElseThrow(() -> new BusinessException(ApiCodes.APPROVAL_STEP_NOT_FOUND,
                        "审批步骤记录不存在: " + stepRecordId));

        ApprovalProcessInstance instance = stepRecord.getProcessInstance();

        if (!instance.isPending()) {
            throw new BusinessException(ApiCodes.APPROVAL_NOT_PENDING,
                    "该审批流程当前状态为 " + instance.getStatus() + "，无法执行转派操作");
        }

        if (!stepRecord.getStepNumber().equals(instance.getCurrentStep())) {
            throw new BusinessException(ApiCodes.APPROVAL_STEP_NOT_CURRENT,
                    "该步骤不是当前待审批步骤");
        }

        if (stepRecord.getStatus() != ApprovalStepStatus.PENDING
                && stepRecord.getStatus() != ApprovalStepStatus.TIMEOUT) {
            throw new BusinessException(ApiCodes.APPROVAL_STEP_NOT_PENDING,
                    "该步骤当前状态为 " + stepRecord.getStatus() + "，无法执行转派操作");
        }

        // 执行转派
        if (newAssignee != null && !newAssignee.isBlank()) {
            stepRecord.setAssigneeRule(ApprovalStepAssigneeRule.BY_USER);
            stepRecord.setAssigneeValue(newAssignee);
        } else {
            stepRecord.setAssigneeRule(ApprovalStepAssigneeRule.BY_ROLE);
            stepRecord.setAssigneeValue(newAssigneeRole);
        }

        stepRecord.transfer(stepRecord.getAssigneeValue());
        // 重置超时时间
        List<ApprovalStep> steps = approvalStepRepository.findByTemplateIdOrderByStepNumberAsc(
                instance.getTemplate().getId());
        ApprovalStep currentStepDef = steps.get(stepRecord.getStepNumber() - 1);
        Integer timeoutHours = resolveTimeoutHours(currentStepDef, instance.getTemplate());
        if (timeoutHours != null && timeoutHours > 0) {
            stepRecord.setTimeoutAt(LocalDateTime.now().plusHours(timeoutHours));
        } else {
            stepRecord.setTimeoutAt(null);
        }

        return approvalStepRecordRepository.save(stepRecord);
    }

    /**
     * 处理超时步骤（由定时任务调用）。
     *
     * @return 处理的超时记录数量
     */
    @Transactional
    public int handleTimeoutSteps() {
        List<ApprovalStepRecord> timeoutRecords = approvalStepRecordRepository.findTimeoutRecords(
                ApprovalStepStatus.PENDING, LocalDateTime.now());

        int count = 0;
        for (ApprovalStepRecord record : timeoutRecords) {
            record.markTimeout();
            approvalStepRecordRepository.save(record);

            ApprovalProcessInstance instance = record.getProcessInstance();
            // 如果所有当前步骤都超时，则标记流程超时（当前 MVP 单步骤超时即标记）
            instance.markTimeout();
            approvalProcessInstanceRepository.save(instance);
            count++;
        }
        return count;
    }

    // =========================================================================
    // 3. 查询
    // =========================================================================

    @Transactional(readOnly = true)
    public Optional<ApprovalProcessInstance> getPendingProcessInstance(UUID administeredItemId) {
        return approvalProcessInstanceRepository
                .findFirstByAdministeredItemIdAndStatusOrderByCreatedAtDesc(
                        administeredItemId, ApprovalProcessStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public ApprovalProcessInstance getProcessInstance(UUID processInstanceId) {
        return approvalProcessInstanceRepository.findById(processInstanceId)
                .orElseThrow(() -> new BusinessException(ApiCodes.APPROVAL_PROCESS_INSTANCE_NOT_FOUND,
                        "审批流程实例不存在: " + processInstanceId));
    }

    @Transactional(readOnly = true)
    public List<ApprovalStepRecord> getStepRecords(UUID processInstanceId) {
        return approvalStepRecordRepository.findByProcessInstanceIdOrderByStepNumberAsc(processInstanceId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalProcessInstance> getProcessHistory(UUID administeredItemId) {
        // 返回该管理项的所有流程实例（按创建时间倒序）
        // 由于 repository 没有直接方法，先用 findAll 后过滤
        return approvalProcessInstanceRepository.findAll().stream()
                .filter(pi -> pi.getAdministeredItemId().equals(administeredItemId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ApprovalStepRecord> getCurrentStepRecord(UUID processInstanceId) {
        return approvalStepRecordRepository.findCurrentStepRecord(processInstanceId);
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

    private void validateApproverPermission(ApprovalStepRecord stepRecord, String approver) {
        ApprovalStepAssigneeRule rule = stepRecord.getAssigneeRule();
        String assigneeValue = stepRecord.getAssigneeValue();

        switch (rule) {
            case BY_USER -> {
                if (!assigneeValue.equals(approver)) {
                    throw new BusinessException(ApiCodes.APPROVAL_UNAUTHORIZED_STEP,
                            "您不是该步骤的指定审批人");
                }
            }
            case BY_ROLE -> {
                // MVP 简化：检查当前用户是否具有指定角色
                // 实际生产环境应查询用户-角色关系
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth == null || !auth.isAuthenticated()) {
                    throw new BusinessException(ApiCodes.APPROVAL_UNAUTHORIZED_STEP,
                            "未登录用户无法执行审批");
                }
                boolean hasRole = auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_" + assigneeValue)
                                || a.getAuthority().equals(assigneeValue));
                if (!hasRole) {
                    throw new BusinessException(ApiCodes.APPROVAL_UNAUTHORIZED_STEP,
                            "您不具有该步骤要求的审批角色: " + assigneeValue);
                }
            }
            case BY_ORGANIZATION -> {
                // MVP 简化：按组织分配 fallback 为按用户名直接匹配
                // V1 阶段需引入组织层级模型后，按用户所属组织进行解析
                if (!assigneeValue.equals(approver)) {
                    throw new BusinessException(ApiCodes.APPROVAL_UNAUTHORIZED_STEP,
                            "您不是该步骤指定组织范围内的审批人");
                }
            }
        }
    }

    private Integer resolveTimeoutHours(ApprovalStep step, ApprovalTemplate template) {
        if (step.getTimeoutHours() != null) {
            return step.getTimeoutHours();
        }
        return template.getDefaultTimeoutHours();
    }

    private String buildTransitionNote(String approver, String opinion) {
        if (opinion == null || opinion.isBlank()) {
            return "多级审批最终通过 by " + approver;
        }
        return "多级审批最终通过 by " + approver + ": " + opinion;
    }

    /**
     * 自动触发 ISO 状态迁移，沿正向路径直至 STANDARD。
     *
     * <p>支持路径：INCOMPLETE → CANDIDATE → RECORDED → QUALIFIED → STANDARD。
     * APPLICATION 可直接迁移至 STANDARD。</p>
     */
    private void transitionToStandard(UUID administeredItemId, String note) {
        AdministeredItem item = administeredItemRepository.findById(administeredItemId)
                .orElseThrow(() -> new BusinessException(ApiCodes.ADMINISTERED_ITEM_NOT_FOUND,
                        "管理项不存在: " + administeredItemId));

        RegistrationStatus current = item.getRegistrationState() == null
                ? RegistrationStatus.INCOMPLETE
                : item.getRegistrationState().getRegistrationStatus();

        if (current == RegistrationStatus.STANDARD || current == RegistrationStatus.PREFERRED_STANDARD) {
            return;
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
            case INCOMPLETE -> RegistrationStatus.CANDIDATE;
            case CANDIDATE -> RegistrationStatus.RECORDED;
            case RECORDED -> RegistrationStatus.QUALIFIED;
            case QUALIFIED -> RegistrationStatus.STANDARD;
            case APPLICATION -> RegistrationStatus.STANDARD;
            default -> throw new BusinessException(ApiCodes.APPROVAL_INVALID_STATE,
                    "无法从状态 " + current + " 自动迁移到 STANDARD");
        };
    }
}
