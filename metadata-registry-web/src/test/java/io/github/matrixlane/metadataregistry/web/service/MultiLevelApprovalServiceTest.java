package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.auth.AppUserRole;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MultiLevelApprovalService} 单元测试。
 *
 * <p>覆盖目标：</p>
 * <ul>
 *   <li>启动审批（正常/模板不存在/已有进行中/管理项不存在）</li>
 *   <li>步骤通过（正常/推进下一级/最后一步触发ISO迁移/非当前步骤/非PENDING/无权限）</li>
 *   <li>步骤退回（整体退回/非当前步骤/空意见）</li>
 *   <li>重新提交（正常/无退回记录/已有进行中）</li>
 *   <li>超时转派（正常/参数校验）</li>
 *   <li>超时处理（定时任务）</li>
 *   <li>查询功能</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MultiLevelApprovalServiceTest {

    @Mock
    private ApprovalTemplateRepository approvalTemplateRepository;
    @Mock
    private ApprovalStepRepository approvalStepRepository;
    @Mock
    private ApprovalProcessInstanceRepository approvalProcessInstanceRepository;
    @Mock
    private ApprovalStepRecordRepository approvalStepRecordRepository;
    @Mock
    private AdministeredItemRepository administeredItemRepository;
    @Mock
    private RegistrationLifecycleService registrationLifecycleService;
    @Mock
    private ApprovalRecordRepository approvalRecordRepository;

    @InjectMocks
    private MultiLevelApprovalService multiLevelApprovalService;

    private UUID itemId;
    private UUID templateId;
    private UUID instanceId;
    private UUID stepRecordId;
    private UUID stepId2;
    private ApprovalTemplate template;
    private ApprovalStep step1;
    private ApprovalStep step2;
    private AdministeredItem item;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        templateId = UUID.randomUUID();
        instanceId = UUID.randomUUID();
        stepRecordId = UUID.randomUUID();
        stepId2 = UUID.randomUUID();

        template = createTemplate();
        step1 = createStep(template, 1, "一级审批", ApprovalStepAssigneeRule.BY_USER, "approver1");
        step2 = createStep(template, 2, "二级审批", ApprovalStepAssigneeRule.BY_ROLE, "ADMIN");

        item = new AdministeredItem();
        item.setId(itemId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // 1. 启动审批测试
    // =========================================================================

    @Test
    @DisplayName("启动审批：正常流程，创建2级审批流程实例和第一步记录")
    void startApproval_shouldCreateProcessInstanceAndFirstStep() {
        authenticateAs("submitter", AppUserRole.REGISTRAR);
        when(administeredItemRepository.existsById(itemId)).thenReturn(true);
        when(approvalTemplateRepository.findByIdAndEnabledTrue(templateId)).thenReturn(Optional.of(template));
        when(approvalProcessInstanceRepository.existsByAdministeredItemIdAndStatus(itemId, ApprovalProcessStatus.PENDING))
                .thenReturn(false);
        when(approvalRecordRepository.existsByAdministeredItemIdAndResult(itemId, ApprovalResult.PENDING))
                .thenReturn(false);
        when(approvalStepRepository.findByTemplateIdOrderByStepNumberAsc(templateId))
                .thenReturn(List.of(step1, step2));
        when(approvalProcessInstanceRepository.save(any())).thenAnswer(inv -> {
            ApprovalProcessInstance i = inv.getArgument(0);
            i.setId(instanceId);
            return i;
        });
        when(approvalStepRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalProcessInstance result = multiLevelApprovalService.startApproval(itemId, templateId);

        assertThat(result.getAdministeredItemId()).isEqualTo(itemId);
        assertThat(result.getTemplate()).isEqualTo(template);
        assertThat(result.getStatus()).isEqualTo(ApprovalProcessStatus.PENDING);
        assertThat(result.getCurrentStep()).isEqualTo(1);
        assertThat(result.getSubmitter()).isEqualTo("submitter");
    }

    @Test
    @DisplayName("启动审批：管理项不存在应抛出异常")
    void startApproval_itemNotFound_shouldThrow() {
        when(administeredItemRepository.existsById(itemId)).thenReturn(false);

        assertThatThrownBy(() -> multiLevelApprovalService.startApproval(itemId, templateId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.ADMINISTERED_ITEM_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("启动审批：模板不存在应抛出异常")
    void startApproval_templateNotFound_shouldThrow() {
        when(administeredItemRepository.existsById(itemId)).thenReturn(true);
        when(approvalTemplateRepository.findByIdAndEnabledTrue(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> multiLevelApprovalService.startApproval(itemId, templateId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_TEMPLATE_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("启动审批：已有进行中流程应抛出异常")
    void startApproval_alreadyPending_shouldThrow() {
        when(administeredItemRepository.existsById(itemId)).thenReturn(true);
        when(approvalTemplateRepository.findByIdAndEnabledTrue(templateId)).thenReturn(Optional.of(template));
        when(approvalProcessInstanceRepository.existsByAdministeredItemIdAndStatus(itemId, ApprovalProcessStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> multiLevelApprovalService.startApproval(itemId, templateId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_PROCESS_ALREADY_PENDING);
                });
    }

    @Test
    @DisplayName("启动审批：已有1级待审批记录应抛出异常")
    void startApproval_hasLevel1Pending_shouldThrow() {
        when(administeredItemRepository.existsById(itemId)).thenReturn(true);
        when(approvalTemplateRepository.findByIdAndEnabledTrue(templateId)).thenReturn(Optional.of(template));
        when(approvalProcessInstanceRepository.existsByAdministeredItemIdAndStatus(itemId, ApprovalProcessStatus.PENDING))
                .thenReturn(false);
        when(approvalRecordRepository.existsByAdministeredItemIdAndResult(itemId, ApprovalResult.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> multiLevelApprovalService.startApproval(itemId, templateId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_ALREADY_PENDING);
                });
    }

    // =========================================================================
    // 2. 步骤通过测试
    // =========================================================================

    @Test
    @DisplayName("步骤通过：第一步通过，推进到第二步")
    void approveStep_firstStep_shouldAdvanceToSecond() {
        authenticateAs("approver1", AppUserRole.ADMIN);
        ApprovalProcessInstance instance = createPendingInstance();
        ApprovalStepRecord stepRecord = createPendingStepRecord(instance, 1, "approver1");

        when(approvalStepRecordRepository.findById(stepRecordId)).thenReturn(Optional.of(stepRecord));
        when(approvalStepRepository.findByTemplateIdOrderByStepNumberAsc(templateId))
                .thenReturn(List.of(step1, step2));
        when(approvalProcessInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(approvalStepRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalStepRecord result = multiLevelApprovalService.approveStep(stepRecordId, "同意");

        assertThat(result.getStatus()).isEqualTo(ApprovalStepStatus.APPROVED);
        assertThat(result.getApprover()).isEqualTo("approver1");
        assertThat(instance.getCurrentStep()).isEqualTo(2);
        assertThat(instance.getStatus()).isEqualTo(ApprovalProcessStatus.PENDING);
    }

    @Test
    @DisplayName("步骤通过：最后一步通过，触发ISO状态迁移")
    void approveStep_lastStep_shouldTriggerIsoTransition() {
        authenticateAs("approver2", AppUserRole.ADMIN);
        ApprovalProcessInstance instance = createPendingInstance();
        instance.setCurrentStep(2);
        ApprovalStepRecord stepRecord = createPendingStepRecord(instance, 2, "ADMIN");
        stepRecord.setAssigneeRule(ApprovalStepAssigneeRule.BY_ROLE);
        item.setRegistrationState(createState(RegistrationStatus.QUALIFIED));

        when(approvalStepRecordRepository.findById(stepRecordId)).thenReturn(Optional.of(stepRecord));
        when(approvalStepRepository.findByTemplateIdOrderByStepNumberAsc(templateId))
                .thenReturn(List.of(step1, step2));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationLifecycleService.transition(any(), any(), any()))
                .thenAnswer(inv -> {
                    RegistrationStatus to = inv.getArgument(1);
                    item.getRegistrationState().setRegistrationStatus(to);
                    return item;
                });
        when(approvalProcessInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(approvalStepRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalStepRecord result = multiLevelApprovalService.approveStep(stepRecordId, "最终通过");

        assertThat(result.getStatus()).isEqualTo(ApprovalStepStatus.APPROVED);
        assertThat(instance.getStatus()).isEqualTo(ApprovalProcessStatus.APPROVED);
        assertThat(instance.getCompletedAt()).isNotNull();
        verify(registrationLifecycleService).transition(eq(itemId), eq(RegistrationStatus.STANDARD), any());
    }

    @Test
    @DisplayName("步骤通过：非当前步骤应抛出异常")
    void approveStep_notCurrentStep_shouldThrow() {
        authenticateAs("approver1", AppUserRole.ADMIN);
        ApprovalProcessInstance instance = createPendingInstance();
        instance.setCurrentStep(2);
        ApprovalStepRecord stepRecord = createPendingStepRecord(instance, 1, "approver1");

        when(approvalStepRecordRepository.findById(stepRecordId)).thenReturn(Optional.of(stepRecord));

        assertThatThrownBy(() -> multiLevelApprovalService.approveStep(stepRecordId, "同意"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_STEP_NOT_CURRENT);
                });
    }

    @Test
    @DisplayName("步骤通过：非PENDING状态应抛出异常")
    void approveStep_notPending_shouldThrow() {
        authenticateAs("approver1", AppUserRole.ADMIN);
        ApprovalProcessInstance instance = createPendingInstance();
        ApprovalStepRecord stepRecord = createPendingStepRecord(instance, 1, "approver1");
        stepRecord.setStatus(ApprovalStepStatus.APPROVED);

        when(approvalStepRecordRepository.findById(stepRecordId)).thenReturn(Optional.of(stepRecord));

        assertThatThrownBy(() -> multiLevelApprovalService.approveStep(stepRecordId, "同意"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_STEP_NOT_PENDING);
                });
    }

    @Test
    @DisplayName("步骤通过：按角色分配，无权限应抛出异常")
    void approveStep_roleAssignee_noPermission_shouldThrow() {
        authenticateAs("randomUser", AppUserRole.REGISTRAR);
        ApprovalProcessInstance instance = createPendingInstance();
        instance.setCurrentStep(2);
        ApprovalStepRecord stepRecord = createPendingStepRecord(instance, 2, "ADMIN");
        stepRecord.setAssigneeRule(ApprovalStepAssigneeRule.BY_ROLE);
        stepRecord.setAssigneeValue("ADMIN");

        when(approvalStepRecordRepository.findById(stepRecordId)).thenReturn(Optional.of(stepRecord));

        assertThatThrownBy(() -> multiLevelApprovalService.approveStep(stepRecordId, "同意"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_UNAUTHORIZED_STEP);
                });
    }

    @Test
    @DisplayName("步骤通过：按组织分配，无权限应抛出异常")
    void approveStep_orgAssignee_noPermission_shouldThrow() {
        authenticateAs("randomUser", AppUserRole.REGISTRAR);
        ApprovalProcessInstance instance = createPendingInstance();
        ApprovalStepRecord stepRecord = createPendingStepRecord(instance, 1, "orgA");
        stepRecord.setAssigneeRule(ApprovalStepAssigneeRule.BY_ORGANIZATION);
        stepRecord.setAssigneeValue("orgA");

        when(approvalStepRecordRepository.findById(stepRecordId)).thenReturn(Optional.of(stepRecord));

        assertThatThrownBy(() -> multiLevelApprovalService.approveStep(stepRecordId, "同意"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_UNAUTHORIZED_STEP);
                });
    }

    @Test
    @DisplayName("步骤通过：按组织分配，匹配成功应通过")
    void approveStep_orgAssignee_match_shouldSucceed() {
        authenticateAs("orgA", AppUserRole.ADMIN);
        ApprovalProcessInstance instance = createPendingInstance();
        ApprovalStepRecord stepRecord = createPendingStepRecord(instance, 1, "orgA");
        stepRecord.setAssigneeRule(ApprovalStepAssigneeRule.BY_ORGANIZATION);
        stepRecord.setAssigneeValue("orgA");

        when(approvalStepRecordRepository.findById(stepRecordId)).thenReturn(Optional.of(stepRecord));
        when(approvalStepRepository.findByTemplateIdOrderByStepNumberAsc(templateId))
                .thenReturn(List.of(step1, step2));
        when(approvalProcessInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(approvalStepRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalStepRecord result = multiLevelApprovalService.approveStep(stepRecordId, "同意");

        assertThat(result.getStatus()).isEqualTo(ApprovalStepStatus.APPROVED);
    }

    @Test
    @DisplayName("步骤通过：流程已结束应抛出异常")
    void approveStep_processCompleted_shouldThrow() {
        authenticateAs("approver1", AppUserRole.ADMIN);
        ApprovalProcessInstance instance = createPendingInstance();
        instance.setStatus(ApprovalProcessStatus.REJECTED);
        ApprovalStepRecord stepRecord = createPendingStepRecord(instance, 1, "approver1");

        when(approvalStepRecordRepository.findById(stepRecordId)).thenReturn(Optional.of(stepRecord));

        assertThatThrownBy(() -> multiLevelApprovalService.approveStep(stepRecordId, "同意"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_NOT_PENDING);
                });
    }

    @Test
    @DisplayName("步骤通过：意见超过2000字符应抛出异常")
    void approveStep_opinionTooLong_shouldThrow() {
        String longOpinion = "a".repeat(2001);

        assertThatThrownBy(() -> multiLevelApprovalService.approveStep(UUID.randomUUID(), longOpinion))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_OPINION_TOO_LONG);
                });
    }

    // =========================================================================
    // 3. 步骤退回测试
    // =========================================================================

    @Test
    @DisplayName("步骤退回：正常流程，整体退回")
    void rejectStep_shouldRejectAll() {
        authenticateAs("approver1", AppUserRole.ADMIN);
        ApprovalProcessInstance instance = createPendingInstance();
        ApprovalStepRecord stepRecord = createPendingStepRecord(instance, 1, "approver1");

        when(approvalStepRecordRepository.findById(stepRecordId)).thenReturn(Optional.of(stepRecord));
        when(approvalStepRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(approvalProcessInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalStepRecord result = multiLevelApprovalService.rejectStep(stepRecordId, "名称不规范");

        assertThat(result.getStatus()).isEqualTo(ApprovalStepStatus.REJECTED);
        assertThat(result.getOpinion()).isEqualTo("名称不规范");
        assertThat(instance.getStatus()).isEqualTo(ApprovalProcessStatus.REJECTED);
        assertThat(instance.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("步骤退回：空意见应抛出异常")
    void rejectStep_blankOpinion_shouldThrow() {
        assertThatThrownBy(() -> multiLevelApprovalService.rejectStep(stepRecordId, "  "))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED);
                });
    }

    @Test
    @DisplayName("步骤退回：null意见应抛出异常")
    void rejectStep_nullOpinion_shouldThrow() {
        assertThatThrownBy(() -> multiLevelApprovalService.rejectStep(stepRecordId, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED);
                });
    }

    @Test
    @DisplayName("步骤退回：非当前步骤应抛出异常")
    void rejectStep_notCurrentStep_shouldThrow() {
        authenticateAs("approver1", AppUserRole.ADMIN);
        ApprovalProcessInstance instance = createPendingInstance();
        instance.setCurrentStep(2);
        ApprovalStepRecord stepRecord = createPendingStepRecord(instance, 1, "approver1");

        when(approvalStepRecordRepository.findById(stepRecordId)).thenReturn(Optional.of(stepRecord));

        assertThatThrownBy(() -> multiLevelApprovalService.rejectStep(stepRecordId, "退回"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_STEP_NOT_CURRENT);
                });
    }

    // =========================================================================
    // 4. 重新提交测试
    // =========================================================================

    @Test
    @DisplayName("重新提交：正常流程，上次为REJECTED")
    void resubmit_afterRejection_shouldStartNewProcess() {
        authenticateAs("submitter", AppUserRole.REGISTRAR);
        ApprovalProcessInstance rejectedInstance = createRejectedInstance();

        when(administeredItemRepository.existsById(itemId)).thenReturn(true);
        when(approvalProcessInstanceRepository.findFirstByAdministeredItemIdAndStatusOrderByCreatedAtDesc(
                itemId, ApprovalProcessStatus.REJECTED)).thenReturn(Optional.of(rejectedInstance));
        when(approvalProcessInstanceRepository.existsByAdministeredItemIdAndStatus(itemId, ApprovalProcessStatus.PENDING))
                .thenReturn(false);
        when(approvalRecordRepository.existsByAdministeredItemIdAndResult(itemId, ApprovalResult.PENDING))
                .thenReturn(false);
        when(approvalTemplateRepository.findByIdAndEnabledTrue(templateId)).thenReturn(Optional.of(template));
        when(approvalStepRepository.findByTemplateIdOrderByStepNumberAsc(templateId))
                .thenReturn(List.of(step1, step2));
        when(approvalProcessInstanceRepository.save(any())).thenAnswer(inv -> {
            ApprovalProcessInstance i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            return i;
        });
        when(approvalStepRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalProcessInstance result = multiLevelApprovalService.resubmit(itemId, null);

        assertThat(result.getStatus()).isEqualTo(ApprovalProcessStatus.PENDING);
        assertThat(result.getCurrentStep()).isEqualTo(1);
    }

    @Test
    @DisplayName("重新提交：无退回记录应抛出异常")
    void resubmit_noRejectedRecord_shouldThrow() {
        when(administeredItemRepository.existsById(itemId)).thenReturn(true);
        when(approvalProcessInstanceRepository.findFirstByAdministeredItemIdAndStatusOrderByCreatedAtDesc(
                itemId, ApprovalProcessStatus.REJECTED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> multiLevelApprovalService.resubmit(itemId, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_NOT_REJECTED);
                });
    }

    @Test
    @DisplayName("重新提交：已有进行中流程应抛出异常")
    void resubmit_alreadyPending_shouldThrow() {
        ApprovalProcessInstance rejectedInstance = createRejectedInstance();

        when(administeredItemRepository.existsById(itemId)).thenReturn(true);
        when(approvalProcessInstanceRepository.findFirstByAdministeredItemIdAndStatusOrderByCreatedAtDesc(
                itemId, ApprovalProcessStatus.REJECTED)).thenReturn(Optional.of(rejectedInstance));
        when(approvalProcessInstanceRepository.existsByAdministeredItemIdAndStatus(itemId, ApprovalProcessStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> multiLevelApprovalService.resubmit(itemId, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_PROCESS_ALREADY_PENDING);
                });
    }

    // =========================================================================
    // 5. 超时转派测试
    // =========================================================================

    @Test
    @DisplayName("超时转派：正常流程，按人转派")
    void transferStep_shouldTransferToNewUser() {
        authenticateAs("admin", AppUserRole.ADMIN);
        ApprovalProcessInstance instance = createPendingInstance();
        ApprovalStepRecord stepRecord = createPendingStepRecord(instance, 1, "approver1");

        when(approvalStepRecordRepository.findById(stepRecordId)).thenReturn(Optional.of(stepRecord));
        when(approvalStepRepository.findByTemplateIdOrderByStepNumberAsc(templateId))
                .thenReturn(List.of(step1, step2));
        when(approvalStepRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalStepRecord result = multiLevelApprovalService.transferStep(stepRecordId, "newApprover", null);

        assertThat(result.getAssigneeRule()).isEqualTo(ApprovalStepAssigneeRule.BY_USER);
        assertThat(result.getAssigneeValue()).isEqualTo("newApprover");
        assertThat(result.getStatus()).isEqualTo(ApprovalStepStatus.TRANSFERRED);
    }

    @Test
    @DisplayName("超时转派：按角色转派")
    void transferStep_toRole_shouldTransferToNewRole() {
        authenticateAs("admin", AppUserRole.ADMIN);
        ApprovalProcessInstance instance = createPendingInstance();
        ApprovalStepRecord stepRecord = createPendingStepRecord(instance, 1, "approver1");

        when(approvalStepRecordRepository.findById(stepRecordId)).thenReturn(Optional.of(stepRecord));
        when(approvalStepRepository.findByTemplateIdOrderByStepNumberAsc(templateId))
                .thenReturn(List.of(step1, step2));
        when(approvalStepRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalStepRecord result = multiLevelApprovalService.transferStep(stepRecordId, null, "MANAGER");

        assertThat(result.getAssigneeRule()).isEqualTo(ApprovalStepAssigneeRule.BY_ROLE);
        assertThat(result.getAssigneeValue()).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("超时转派：未指定新审批人应抛出异常")
    void transferStep_noAssignee_shouldThrow() {
        assertThatThrownBy(() -> multiLevelApprovalService.transferStep(stepRecordId, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED);
                });
    }

    // =========================================================================
    // 6. 超时处理测试
    // =========================================================================

    @Test
    @DisplayName("超时处理：标记超时步骤和流程")
    void handleTimeoutSteps_shouldMarkTimeout() {
        ApprovalProcessInstance instance = createPendingInstance();
        ApprovalStepRecord stepRecord = createPendingStepRecord(instance, 1, "approver1");
        stepRecord.setTimeoutAt(LocalDateTime.now().minusMinutes(1));

        when(approvalStepRecordRepository.findTimeoutRecords(eq(ApprovalStepStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(stepRecord));
        when(approvalStepRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(approvalProcessInstanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int count = multiLevelApprovalService.handleTimeoutSteps();

        assertThat(count).isEqualTo(1);
        assertThat(stepRecord.getStatus()).isEqualTo(ApprovalStepStatus.TIMEOUT);
        assertThat(instance.getStatus()).isEqualTo(ApprovalProcessStatus.TIMEOUT);
    }

    @Test
    @DisplayName("超时处理：无超时记录")
    void handleTimeoutSteps_noTimeout_shouldReturnZero() {
        when(approvalStepRecordRepository.findTimeoutRecords(eq(ApprovalStepStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of());

        int count = multiLevelApprovalService.handleTimeoutSteps();

        assertThat(count).isEqualTo(0);
    }

    // =========================================================================
    // 7. 查询测试
    // =========================================================================

    @Test
    @DisplayName("查询流程实例：正常返回")
    void getProcessInstance_shouldReturn() {
        ApprovalProcessInstance instance = createPendingInstance();
        when(approvalProcessInstanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        ApprovalProcessInstance result = multiLevelApprovalService.getProcessInstance(instanceId);

        assertThat(result.getId()).isEqualTo(instanceId);
    }

    @Test
    @DisplayName("查询流程实例：不存在应抛出异常")
    void getProcessInstance_notFound_shouldThrow() {
        when(approvalProcessInstanceRepository.findById(instanceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> multiLevelApprovalService.getProcessInstance(instanceId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_PROCESS_INSTANCE_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("查询进行中的流程实例：正常返回")
    void getPendingProcessInstance_shouldReturn() {
        ApprovalProcessInstance instance = createPendingInstance();
        when(approvalProcessInstanceRepository.findFirstByAdministeredItemIdAndStatusOrderByCreatedAtDesc(
                itemId, ApprovalProcessStatus.PENDING)).thenReturn(Optional.of(instance));

        Optional<ApprovalProcessInstance> result = multiLevelApprovalService.getPendingProcessInstance(itemId);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(instanceId);
    }

    @Test
    @DisplayName("查询进行中的流程实例：不存在返回空")
    void getPendingProcessInstance_notFound_shouldReturnEmpty() {
        when(approvalProcessInstanceRepository.findFirstByAdministeredItemIdAndStatusOrderByCreatedAtDesc(
                itemId, ApprovalProcessStatus.PENDING)).thenReturn(Optional.empty());

        Optional<ApprovalProcessInstance> result = multiLevelApprovalService.getPendingProcessInstance(itemId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("查询步骤记录列表：正常返回")
    void getStepRecords_shouldReturn() {
        ApprovalProcessInstance instance = createPendingInstance();
        ApprovalStepRecord record = createPendingStepRecord(instance, 1, "approver1");
        when(approvalStepRecordRepository.findByProcessInstanceIdOrderByStepNumberAsc(instanceId))
                .thenReturn(List.of(record));

        List<ApprovalStepRecord> result = multiLevelApprovalService.getStepRecords(instanceId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStepNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("查询当前步骤记录：正常返回")
    void getCurrentStepRecord_shouldReturn() {
        ApprovalProcessInstance instance = createPendingInstance();
        ApprovalStepRecord record = createPendingStepRecord(instance, 1, "approver1");
        when(approvalStepRecordRepository.findCurrentStepRecord(instanceId))
                .thenReturn(Optional.of(record));

        Optional<ApprovalStepRecord> result = multiLevelApprovalService.getCurrentStepRecord(instanceId);

        assertThat(result).isPresent();
        assertThat(result.get().getStepNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("查询流程历史：正常返回")
    void getProcessHistory_shouldReturn() {
        ApprovalProcessInstance instance1 = createPendingInstance();
        instance1.setCreatedAt(LocalDateTime.now().minusDays(1));
        ApprovalProcessInstance instance2 = createRejectedInstance();
        instance2.setCreatedAt(LocalDateTime.now());

        when(approvalProcessInstanceRepository.findAll()).thenReturn(List.of(instance1, instance2));

        List<ApprovalProcessInstance> result = multiLevelApprovalService.getProcessHistory(itemId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatus()).isEqualTo(ApprovalProcessStatus.REJECTED);
    }

    // =========================================================================
    // Helper 方法
    // =========================================================================

    private void authenticateAs(String username, AppUserRole role) {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), username, role == AppUserRole.ADMIN ? SystemRole.SUPER_ADMIN : null);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority(role.authority())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private ApprovalTemplate createTemplate() {
        ApprovalTemplate t = new ApprovalTemplate();
        t.setId(templateId);
        t.setName("2级审批模板");
        t.setStepCount(2);
        t.setEnabled(true);
        t.setLocked(true);
        t.setDefaultTimeoutHours(72);
        t.setDefaultRejectStrategy(RejectStrategy.REJECT_ALL);
        return t;
    }

    private ApprovalStep createStep(ApprovalTemplate template, int number, String name,
                                     ApprovalStepAssigneeRule rule, String value) {
        ApprovalStep s = new ApprovalStep();
        s.setId(UUID.randomUUID());
        s.setTemplate(template);
        s.setStepNumber(number);
        s.setStepName(name);
        s.setAssigneeRule(rule);
        s.setAssigneeValue(value);
        s.setTimeoutHours(72);
        return s;
    }

    private ApprovalProcessInstance createPendingInstance() {
        ApprovalProcessInstance i = new ApprovalProcessInstance();
        i.setId(instanceId);
        i.setTemplate(template);
        i.setAdministeredItemId(itemId);
        i.setSubmitter("submitter");
        i.setCurrentStep(1);
        i.setStatus(ApprovalProcessStatus.PENDING);
        i.setCreatedAt(LocalDateTime.now());
        return i;
    }

    private ApprovalProcessInstance createRejectedInstance() {
        ApprovalProcessInstance i = createPendingInstance();
        i.setStatus(ApprovalProcessStatus.REJECTED);
        i.setCompletedAt(LocalDateTime.now());
        return i;
    }

    private ApprovalStepRecord createPendingStepRecord(ApprovalProcessInstance instance, int stepNumber,
                                                        String assigneeValue) {
        ApprovalStepRecord r = new ApprovalStepRecord();
        r.setId(stepRecordId);
        r.setProcessInstance(instance);
        r.setStepNumber(stepNumber);
        r.setStepName("第" + stepNumber + "级审批");
        r.setAssigneeRule(ApprovalStepAssigneeRule.BY_USER);
        r.setAssigneeValue(assigneeValue);
        r.setAssignedAt(LocalDateTime.now());
        r.setStatus(ApprovalStepStatus.PENDING);
        r.setTimeoutAt(LocalDateTime.now().plusHours(72));
        return r;
    }

    private RegistrationState createState(RegistrationStatus status) {
        RegistrationState state = new RegistrationState();
        state.setId(UUID.randomUUID());
        state.setRegistrationStatus(status);
        return state;
    }
}
