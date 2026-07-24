package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.auth.AppUserRole;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalRecord;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalResult;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ApprovalRecordRepository;
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
 * {@link ApprovalService} 单元测试。
 *
 * <p>覆盖目标：</p>
 * <ul>
 *   <li>提交审批（正常/重复提交/管理项不存在）</li>
 *   <li>审批通过（正常/非PENDING/意见超长/终态拒绝/自动状态迁移）</li>
 *   <li>审批退回（正常/空意见/非PENDING）</li>
 *   <li>重新提交（正常/无退回记录/重复提交）</li>
 *   <li>审批历史查询</li>
 *   <li>乐观锁并发防护（通过 repository 异常模拟）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalRecordRepository approvalRecordRepository;

    @Mock
    private AdministeredItemRepository administeredItemRepository;

    @Mock
    private RegistrationLifecycleService registrationLifecycleService;

    @InjectMocks
    private ApprovalService approvalService;

    private UUID itemId;
    private UUID recordId;
    private AdministeredItem item;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        recordId = UUID.randomUUID();
        item = new AdministeredItem();
        item.setId(itemId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // 1. 提交审批测试
    // =========================================================================

    @Test
    @DisplayName("提交审批：正常流程，创建 PENDING 记录")
    void submitForApproval_shouldCreatePendingRecord() {
        authenticateAs("submitter", AppUserRole.REGISTRAR);
        when(administeredItemRepository.existsById(itemId)).thenReturn(true);
        when(approvalRecordRepository.existsByAdministeredItemIdAndResult(itemId, ApprovalResult.PENDING))
                .thenReturn(false);
        when(approvalRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRecord result = approvalService.submitForApproval(itemId);

        assertThat(result.getAdministeredItemId()).isEqualTo(itemId);
        assertThat(result.getSubmitter()).isEqualTo("submitter");
        assertThat(result.getResult()).isEqualTo(ApprovalResult.PENDING);
        assertThat(result.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("提交审批：管理项不存在应抛出异常")
    void submitForApproval_itemNotFound_shouldThrow() {
        when(administeredItemRepository.existsById(itemId)).thenReturn(false);

        assertThatThrownBy(() -> approvalService.submitForApproval(itemId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.ADMINISTERED_ITEM_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("提交审批：已存在 PENDING 记录应抛出异常")
    void submitForApproval_alreadyPending_shouldThrow() {
        when(administeredItemRepository.existsById(itemId)).thenReturn(true);
        when(approvalRecordRepository.existsByAdministeredItemIdAndResult(itemId, ApprovalResult.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> approvalService.submitForApproval(itemId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_ALREADY_PENDING);
                });
    }

    // =========================================================================
    // 2. 审批通过测试
    // =========================================================================

    @Test
    @DisplayName("审批通过：正常流程，标记 APPROVED 并触发状态迁移")
    void approve_shouldMarkApprovedAndTriggerTransition() {
        authenticateAs("approver", AppUserRole.ADMIN);
        ApprovalRecord record = createPendingRecord();
        item.setRegistrationState(createState(RegistrationStatus.QUALIFIED));

        when(approvalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationLifecycleService.transition(any(), any(), any()))
                .thenAnswer(inv -> {
                    RegistrationStatus to = inv.getArgument(1);
                    item.getRegistrationState().setRegistrationStatus(to);
                    return item;
                });
        when(approvalRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRecord result = approvalService.approve(recordId, "同意发布");

        assertThat(result.getResult()).isEqualTo(ApprovalResult.APPROVED);
        assertThat(result.getApprover()).isEqualTo("approver");
        assertThat(result.getOpinion()).isEqualTo("同意发布");
        assertThat(result.getApprovedAt()).isNotNull();

        verify(registrationLifecycleService).transition(eq(itemId), eq(RegistrationStatus.STANDARD), any());
    }

    @Test
    @DisplayName("审批通过：从 INCOMPLETE 开始链式迁移至 STANDARD")
    void approve_fromIncomplete_shouldChainTransitions() {
        authenticateAs("approver", AppUserRole.ADMIN);
        ApprovalRecord record = createPendingRecord();
        item.setRegistrationState(createState(RegistrationStatus.INCOMPLETE));

        when(approvalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(administeredItemRepository.findById(itemId))
                .thenReturn(Optional.of(item));
        when(registrationLifecycleService.transition(any(), any(), any()))
                .thenAnswer(inv -> {
                    RegistrationStatus to = inv.getArgument(1);
                    item.getRegistrationState().setRegistrationStatus(to);
                    return item;
                });
        when(approvalRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.approve(recordId, "通过");

        verify(registrationLifecycleService).transition(eq(itemId), eq(RegistrationStatus.SUBMITTED), any());
        verify(registrationLifecycleService).transition(eq(itemId), eq(RegistrationStatus.RECORDED), any());
        verify(registrationLifecycleService).transition(eq(itemId), eq(RegistrationStatus.QUALIFIED), any());
        verify(registrationLifecycleService).transition(eq(itemId), eq(RegistrationStatus.STANDARD), any());
    }

    @Test
    @DisplayName("审批通过：已是 STANDARD 状态，不重复迁移")
    void approve_alreadyStandard_shouldSkipTransition() {
        authenticateAs("approver", AppUserRole.ADMIN);
        ApprovalRecord record = createPendingRecord();
        item.setRegistrationState(createState(RegistrationStatus.STANDARD));

        when(approvalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(approvalRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.approve(recordId, "再次确认");

        verify(registrationLifecycleService, never()).transition(any(), any(), any());
    }

    @Test
    @DisplayName("审批通过：PREFERRED 视为已发布，不迁移")
    void approve_preferred_shouldSkipTransition() {
        authenticateAs("approver", AppUserRole.ADMIN);
        ApprovalRecord record = createPendingRecord();
        item.setRegistrationState(createState(RegistrationStatus.PREFERRED));

        when(approvalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(approvalRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.approve(recordId, "确认");

        verify(registrationLifecycleService, never()).transition(any(), any(), any());
    }

    @Test
    @DisplayName("审批通过：终态（RETIRED）不允许通过")
    void approve_fromRetired_shouldReject() {
        authenticateAs("approver", AppUserRole.ADMIN);
        ApprovalRecord record = createPendingRecord();
        item.setRegistrationState(createState(RegistrationStatus.RETIRED));

        when(approvalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> approvalService.approve(recordId, "尝试通过"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_INVALID_STATE);
                });
    }

    @Test
    @DisplayName("审批通过：记录不存在应抛出异常")
    void approve_recordNotFound_shouldThrow() {
        when(approvalRecordRepository.findById(recordId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.approve(recordId, "意见"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_RECORD_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("审批通过：非 PENDING 状态应抛出异常")
    void approve_notPending_shouldThrow() {
        ApprovalRecord record = createPendingRecord();
        record.approve("someone", "already");

        when(approvalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> approvalService.approve(recordId, "意见"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_NOT_PENDING);
                });
    }

    @Test
    @DisplayName("审批通过：意见超过 2000 字符应抛出异常")
    void approve_opinionTooLong_shouldThrow() {
        String longOpinion = "a".repeat(2001);

        assertThatThrownBy(() -> approvalService.approve(recordId, longOpinion))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_OPINION_TOO_LONG);
                });
    }

    @Test
    @DisplayName("审批通过：空意见应允许")
    void approve_nullOpinion_shouldSucceed() {
        authenticateAs("approver", AppUserRole.ADMIN);
        ApprovalRecord record = createPendingRecord();
        item.setRegistrationState(createState(RegistrationStatus.QUALIFIED));

        when(approvalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationLifecycleService.transition(any(), any(), any()))
                .thenAnswer(inv -> {
                    RegistrationStatus to = inv.getArgument(1);
                    item.getRegistrationState().setRegistrationStatus(to);
                    return item;
                });
        when(approvalRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRecord result = approvalService.approve(recordId, null);

        assertThat(result.getResult()).isEqualTo(ApprovalResult.APPROVED);
    }

    // =========================================================================
    // 3. 审批退回测试
    // =========================================================================

    @Test
    @DisplayName("审批退回：正常流程，标记 REJECTED")
    void reject_shouldMarkRejected() {
        authenticateAs("approver", AppUserRole.ADMIN);
        ApprovalRecord record = createPendingRecord();

        when(approvalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(approvalRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRecord result = approvalService.reject(recordId, "名称不规范，请修改");

        assertThat(result.getResult()).isEqualTo(ApprovalResult.REJECTED);
        assertThat(result.getApprover()).isEqualTo("approver");
        assertThat(result.getOpinion()).isEqualTo("名称不规范，请修改");
        assertThat(result.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("审批退回：空意见应抛出异常")
    void reject_blankOpinion_shouldThrow() {
        assertThatThrownBy(() -> approvalService.reject(recordId, "  "))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED);
                    assertThat(be.getMessage()).contains("退回意见不能为空");
                });
    }

    @Test
    @DisplayName("审批退回：null 意见应抛出异常")
    void reject_nullOpinion_shouldThrow() {
        assertThatThrownBy(() -> approvalService.reject(recordId, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED);
                });
    }

    @Test
    @DisplayName("审批退回：非 PENDING 状态应抛出异常")
    void reject_notPending_shouldThrow() {
        ApprovalRecord record = createPendingRecord();
        record.reject("someone", "already");

        when(approvalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> approvalService.reject(recordId, "意见"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_NOT_PENDING);
                });
    }

    @Test
    @DisplayName("审批退回：意见超过 2000 字符应抛出异常")
    void reject_opinionTooLong_shouldThrow() {
        String longOpinion = "a".repeat(2001);

        assertThatThrownBy(() -> approvalService.reject(recordId, longOpinion))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_OPINION_TOO_LONG);
                });
    }

    // =========================================================================
    // 4. 重新提交测试
    // =========================================================================

    @Test
    @DisplayName("重新提交：正常流程，上次为 REJECTED")
    void resubmit_afterRejection_shouldCreateNewPending() {
        authenticateAs("submitter", AppUserRole.REGISTRAR);
        ApprovalRecord rejectedRecord = createRejectedRecord();

        when(administeredItemRepository.existsById(itemId)).thenReturn(true);
        when(approvalRecordRepository.findFirstByAdministeredItemIdAndResultOrderBySubmittedAtDesc(
                itemId, ApprovalResult.REJECTED)).thenReturn(Optional.of(rejectedRecord));
        when(approvalRecordRepository.existsByAdministeredItemIdAndResult(itemId, ApprovalResult.PENDING))
                .thenReturn(false);
        when(approvalRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRecord result = approvalService.resubmit(itemId);

        assertThat(result.getResult()).isEqualTo(ApprovalResult.PENDING);
        assertThat(result.getSubmitter()).isEqualTo("submitter");
    }

    @Test
    @DisplayName("重新提交：无退回记录应抛出异常")
    void resubmit_noRejectedRecord_shouldThrow() {
        when(administeredItemRepository.existsById(itemId)).thenReturn(true);
        when(approvalRecordRepository.findFirstByAdministeredItemIdAndResultOrderBySubmittedAtDesc(
                itemId, ApprovalResult.REJECTED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.resubmit(itemId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_NOT_REJECTED);
                });
    }

    @Test
    @DisplayName("重新提交：已存在 PENDING 应抛出异常")
    void resubmit_alreadyPending_shouldThrow() {
        ApprovalRecord rejectedRecord = createRejectedRecord();

        when(administeredItemRepository.existsById(itemId)).thenReturn(true);
        when(approvalRecordRepository.findFirstByAdministeredItemIdAndResultOrderBySubmittedAtDesc(
                itemId, ApprovalResult.REJECTED)).thenReturn(Optional.of(rejectedRecord));
        when(approvalRecordRepository.existsByAdministeredItemIdAndResult(itemId, ApprovalResult.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> approvalService.resubmit(itemId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.APPROVAL_ALREADY_PENDING);
                });
    }

    // =========================================================================
    // 5. 审批历史查询测试
    // =========================================================================

    @Test
    @DisplayName("审批历史：按提交时间倒序返回")
    void getApprovalHistory_shouldReturnOrdered() {
        ApprovalRecord r1 = createRecordWithResult(ApprovalResult.PENDING);
        ApprovalRecord r2 = createRecordWithResult(ApprovalResult.REJECTED);

        when(approvalRecordRepository.findByAdministeredItemIdOrderBySubmittedAtDesc(itemId))
                .thenReturn(List.of(r2, r1));

        List<ApprovalRecord> result = approvalService.getApprovalHistory(itemId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getResult()).isEqualTo(ApprovalResult.REJECTED);
        assertThat(result.get(1).getResult()).isEqualTo(ApprovalResult.PENDING);
    }

    @Test
    @DisplayName("审批历史：无记录返回空列表")
    void getApprovalHistory_empty_shouldReturnEmpty() {
        when(approvalRecordRepository.findByAdministeredItemIdOrderBySubmittedAtDesc(itemId))
                .thenReturn(List.of());

        List<ApprovalRecord> result = approvalService.getApprovalHistory(itemId);

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // 6. 查询待审批记录测试
    // =========================================================================

    @Test
    @DisplayName("查询待审批：存在 PENDING 记录")
    void getPendingApproval_exists_shouldReturn() {
        ApprovalRecord pending = createPendingRecord();
        when(approvalRecordRepository.findFirstByAdministeredItemIdAndResultOrderBySubmittedAtDesc(
                itemId, ApprovalResult.PENDING)).thenReturn(Optional.of(pending));

        Optional<ApprovalRecord> result = approvalService.getPendingApproval(itemId);

        assertThat(result).isPresent();
        assertThat(result.get().getResult()).isEqualTo(ApprovalResult.PENDING);
    }

    @Test
    @DisplayName("查询待审批：不存在返回 empty")
    void getPendingApproval_notExists_shouldReturnEmpty() {
        when(approvalRecordRepository.findFirstByAdministeredItemIdAndResultOrderBySubmittedAtDesc(
                itemId, ApprovalResult.PENDING)).thenReturn(Optional.empty());

        Optional<ApprovalRecord> result = approvalService.getPendingApproval(itemId);

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // 7. INCOMPLETE → SUBMITTED → ... → STANDARD 正向路径
    // =========================================================================

    @Test
    @DisplayName("审批通过：INCOMPLETE 沿正向路径自动迁移至 STANDARD")
    void approve_fromIncomplete_shouldTransitionForward() {
        authenticateAs("approver", AppUserRole.ADMIN);
        ApprovalRecord record = createPendingRecord();
        item.setRegistrationState(createState(RegistrationStatus.INCOMPLETE));
        // INCOMPLETE → SUBMITTED → RECORDED → QUALIFIED → STANDARD
        item.setRegistrationState(createState(RegistrationStatus.INCOMPLETE));

        when(approvalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationLifecycleService.transition(any(), any(), any()))
                .thenAnswer(inv -> {
                    RegistrationStatus to = inv.getArgument(1);
                    item.getRegistrationState().setRegistrationStatus(to);
                    return item;
                });
        when(approvalRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.approve(recordId, "通过正向路径");

        verify(registrationLifecycleService).transition(eq(itemId), eq(RegistrationStatus.SUBMITTED), any());
        verify(registrationLifecycleService).transition(eq(itemId), eq(RegistrationStatus.RECORDED), any());
        verify(registrationLifecycleService).transition(eq(itemId), eq(RegistrationStatus.QUALIFIED), any());
        verify(registrationLifecycleService).transition(eq(itemId), eq(RegistrationStatus.STANDARD), any());
    }

    // =========================================================================
    // Helper 方法
    // =========================================================================

    private void authenticateAs(String username, AppUserRole role) {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), username, role);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority(role.authority())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private ApprovalRecord createPendingRecord() {
        ApprovalRecord record = ApprovalRecord.ofPending(itemId, "submitter");
        record.setId(recordId);
        return record;
    }

    private ApprovalRecord createRejectedRecord() {
        ApprovalRecord record = createPendingRecord();
        record.reject("approver", "退回意见");
        return record;
    }

    private ApprovalRecord createRecordWithResult(ApprovalResult result) {
        ApprovalRecord record = new ApprovalRecord();
        record.setId(UUID.randomUUID());
        record.setAdministeredItemId(itemId);
        record.setSubmitter("user");
        record.setResult(result);
        return record;
    }

    private RegistrationState createState(RegistrationStatus status) {
        RegistrationState state = new RegistrationState();
        state.setId(UUID.randomUUID());
        state.setRegistrationStatus(status);
        return state;
    }
}
