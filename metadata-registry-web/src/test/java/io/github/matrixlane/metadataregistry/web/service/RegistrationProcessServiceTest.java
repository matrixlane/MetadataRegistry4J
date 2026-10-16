package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.ApproverMode;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.QuorumType;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationTransitionApproval;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationTransitionRule;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.StewardshipRecord;
import io.github.matrixlane.metadataregistry.web.auth.AppUserRole;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.registration.ApprovalResultResponse;
import io.github.matrixlane.metadataregistry.web.dto.registration.AvailableTransitionView;
import io.github.matrixlane.metadataregistry.web.dto.registration.RegistrationProcessResponse;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionApprovalRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionRuleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RegistrationProcessService} 单元测试（registration-process-designer）。
 *
 * <p>覆盖目标：</p>
 * <ul>
 *   <li>种子幂等：重复播种不产生重复规则、不覆盖已有规则、默认审核点恰为 10 处</li>
 *   <li>流程定义：当前 RA 解析（活动 Registrar 身份 → 首个 RA → 1123）</li>
 *   <li>迁移动作视图：审批进度、quorum、canExecute/canApprove/approvedByMe 权限分支</li>
 *   <li>执行：直接执行、需审核迁移拒绝（1120）、无启用规则拒绝</li>
 *   <li>批准：合格 Registrar 判定、去重（1122）、quorum 边界（ANY_ONE/AT_LEAST）、
 *       达额生效并清除审批、生效校验失败回滚传播、状态变化清陈旧审批</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RegistrationProcessServiceTest {

    @Mock
    private RegistrationTransitionRuleRepository ruleRepository;
    @Mock
    private RegistrationTransitionApprovalRepository approvalRepository;
    @Mock
    private AdministeredItemRepository administeredItemRepository;
    @Mock
    private RegistrationAuthorityRepository registrationAuthorityRepository;
    @Mock
    private ActiveRegistrarService activeRegistrarService;
    @Mock
    private RegistrationLifecycleService registrationLifecycleService;
    @Mock
    private AdministrativeStatusService administrativeStatusService;
    @Mock
    private ItemAdministrationService itemAdministrationService;

    @InjectMocks
    private RegistrationProcessService registrationProcessService;

    private UUID raId;
    private UUID itemId;
    private UUID accountId;
    private RegistrationAuthority ra;
    private AdministeredItem item;

    @BeforeEach
    void setUp() {
        raId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        ra = authority(raId);
        item = item(itemId, RegistrationStatus.RECORDED, ra);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // 1. 种子播种
    // =========================================================================

    @Test
    @DisplayName("ensureSeed：播种标准底线全部迁移，默认审核点恰为 10 处")
    void ensureSeed_createsStandardRulesWith10ApprovalPoints() {
        List<RegistrationTransitionRule> saved = stubSeedPersistence();

        registrationProcessService.ensureSeed(raId);

        // ISO/IEC 11179-6:2023 Annex D.2.2–D.2.11 共 20 条迁移
        assertThat(saved).hasSize(20);
        List<RegistrationTransitionRule> approvalRules = saved.stream()
                .filter(RegistrationTransitionRule::isRequiresApproval)
                .toList();
        assertThat(approvalRules).hasSize(10);
        assertThat(approvalRules).extracting(r -> r.getFromStatus() + "→" + r.getToStatus())
                .containsExactlyInAnyOrder("RECORDED→QUALIFIED",
                        "QUALIFIED→STANDARD", "STANDARD→PREFERRED_STANDARD",
                        // D.4.2/D.4.3 Qualified 及以上降级 + D.2.9 SUPERSEDED→RETIRED
                        "QUALIFIED→RETIRED", "STANDARD→RETIRED", "PREFERRED_STANDARD→RETIRED",
                        "QUALIFIED→SUPERSEDED", "STANDARD→SUPERSEDED",
                        "PREFERRED_STANDARD→SUPERSEDED", "SUPERSEDED→RETIRED");
        // 默认审核配置：全部 Registrar、任一人通过、启用
        assertThat(approvalRules).allSatisfy(r -> {
            assertThat(r.getApproverMode()).isEqualTo(ApproverMode.ALL_REGISTRARS);
            assertThat(r.getQuorumType()).isEqualTo(QuorumType.ANY_ONE);
            assertThat(r.getQuorumCount()).isNull();
            assertThat(r.isEnabled()).isTrue();
            assertThat(r.getRegistrationAuthority()).isSameAs(ra);
        });
        // CANDIDATE→RECORDED 按 4.4 c) 无需批准（2026-09-19 治理加严已撤销）
        assertThat(saved).filteredOn(r -> r.getFromStatus() == RegistrationStatus.CANDIDATE
                        && r.getToStatus() == RegistrationStatus.RECORDED)
                .singleElement()
                .satisfies(r -> assertThat(r.isRequiresApproval()).isFalse());
    }

    @Test
    @DisplayName("ensureSeed：重复播种幂等，不产生重复规则")
    void ensureSeed_idempotent_secondRunSavesNothing() {
        stubSeedPersistence();
        registrationProcessService.ensureSeed(raId);
        registrationProcessService.ensureSeed(raId);
        registrationProcessService.ensureSeed(raId);

        verify(ruleRepository, times(20)).saveAndFlush(any());
    }

    @Test
    @DisplayName("ensureSeed：调和种子规则——删除标准集外的种子项、保留定制规则、插入缺失项")
    void ensureSeed_reconcilesSeedRulesAndPreservesCustomized() {
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));

        // 存量规则：一条标准集外的旧种子（APPLICATION→RETIRED，displayName 空）→ 应删除；
        // 一条标准集外但已定制的规则（RETIRED→HISTORICAL，displayName 非空）→ 应保留；
        // (RECORDED, QUALIFIED) 已有规则（含定制 displayName）→ 不重复插入、不删除
        RegistrationTransitionRule staleSeed = rule(ra, RegistrationStatus.APPLICATION,
                RegistrationStatus.RETIRED, false, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        RegistrationTransitionRule customized = rule(ra, RegistrationStatus.RETIRED,
                RegistrationStatus.HISTORICAL, false, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        customized.setDisplayName("归档");
        RegistrationTransitionRule customizedApproval = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.SELECTED_REGISTRARS, QuorumType.AT_LEAST, 2);
        customizedApproval.setDisplayName("评审");
        when(ruleRepository.findByRegistrationAuthorityId(raId))
                .thenReturn(List.of(staleSeed, customized, customizedApproval));
        when(ruleRepository.existsByRegistrationAuthorityIdAndFromStatusAndToStatus(
                eq(raId), any(), any()))
                .thenAnswer(inv -> RegistrationStatus.RECORDED == inv.getArgument(1)
                        && RegistrationStatus.QUALIFIED == inv.getArgument(2));

        registrationProcessService.ensureSeed(raId);

        // 旧种子删除；定制规则（无论是否在标准集内）不删除
        verify(ruleRepository).delete(staleSeed);
        verify(ruleRepository, never()).delete(customized);
        verify(ruleRepository, never()).delete(customizedApproval);
        // 20 条标准集 - 1 条已存在（RECORDED→QUALIFIED）= 19 条新插入；不重建定制规则
        ArgumentCaptor<RegistrationTransitionRule> captor =
                ArgumentCaptor.forClass(RegistrationTransitionRule.class);
        verify(ruleRepository, times(19)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).noneMatch(r ->
                r.getFromStatus() == RegistrationStatus.RECORDED
                        && r.getToStatus() == RegistrationStatus.QUALIFIED);
    }

    @Test
    @DisplayName("ensureSeed：既有种子规则字段漂移归位——旧库 QUALIFIED→RETIRED(false) 翻转为需审核")
    void ensureSeed_normalizesDriftedSeedRule() {
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));

        // 旧库种子：QUALIFIED→RETIRED 仍为 requiresApproval=false（D.4.2 纳入审核点前的旧库），
        // 且审核配置漂移
        RegistrationTransitionRule drifted = rule(ra, RegistrationStatus.QUALIFIED,
                RegistrationStatus.RETIRED, false, ApproverMode.SELECTED_REGISTRARS,
                QuorumType.AT_LEAST, 3);
        drifted.setEnabled(false);
        when(ruleRepository.findByRegistrationAuthorityId(raId)).thenReturn(List.of(drifted));
        when(ruleRepository.existsByRegistrationAuthorityIdAndFromStatusAndToStatus(
                eq(raId), any(), any()))
                .thenAnswer(inv -> RegistrationStatus.QUALIFIED == inv.getArgument(1)
                        && RegistrationStatus.RETIRED == inv.getArgument(2));

        registrationProcessService.ensureSeed(raId);

        // 种子规则（displayName 空）调和到默认定义：QUALIFIED→RETIRED 属 D.4.2 审核点
        assertThat(drifted.isRequiresApproval()).isTrue();
        assertThat(drifted.getApproverMode()).isEqualTo(ApproverMode.ALL_REGISTRARS);
        assertThat(drifted.getQuorumType()).isEqualTo(QuorumType.ANY_ONE);
        assertThat(drifted.getQuorumCount()).isNull();
        assertThat(drifted.isEnabled()).isTrue();
        // 19 条缺失插入 + 1 条归位保存 = 20 次 saveAndFlush；不重建该规则
        ArgumentCaptor<RegistrationTransitionRule> captor =
                ArgumentCaptor.forClass(RegistrationTransitionRule.class);
        verify(ruleRepository, times(20)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).filteredOn(r -> r != drifted).hasSize(19);
    }

    @Test
    @DisplayName("ensureSeed：字段无漂移的既有种子规则不触发多余写")
    void ensureSeed_noDrift_noExtraSave() {
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));

        // 健康种子（与默认定义一致的 D.4.2 审核点 QUALIFIED→RETIRED）
        RegistrationTransitionRule healthy = rule(ra, RegistrationStatus.QUALIFIED,
                RegistrationStatus.RETIRED, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(ruleRepository.findByRegistrationAuthorityId(raId)).thenReturn(List.of(healthy));
        when(ruleRepository.existsByRegistrationAuthorityIdAndFromStatusAndToStatus(
                eq(raId), any(), any()))
                .thenAnswer(inv -> RegistrationStatus.QUALIFIED == inv.getArgument(1)
                        && RegistrationStatus.RETIRED == inv.getArgument(2));

        registrationProcessService.ensureSeed(raId);

        // 仅 19 条缺失插入，健康种子规则零重写
        verify(ruleRepository, times(19)).saveAndFlush(any());
    }

    @Test
    @DisplayName("ensureSeed：旧库 CANDIDATE→RECORDED 加严审核点按 4.4 c) 归位撤销（翻转为免审核）")
    void ensureSeed_normalizesRescindedCandidateToRecordedApproval() {
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));

        // 旧库种子：CANDIDATE→RECORDED 仍为 requiresApproval=true（2026-09-19 治理加严时期播种）
        RegistrationTransitionRule rescinded = rule(ra, RegistrationStatus.CANDIDATE,
                RegistrationStatus.RECORDED, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(ruleRepository.findByRegistrationAuthorityId(raId)).thenReturn(List.of(rescinded));
        when(ruleRepository.existsByRegistrationAuthorityIdAndFromStatusAndToStatus(
                eq(raId), any(), any()))
                .thenAnswer(inv -> RegistrationStatus.CANDIDATE == inv.getArgument(1)
                        && RegistrationStatus.RECORDED == inv.getArgument(2));

        registrationProcessService.ensureSeed(raId);

        // 4.4 c)：submitter/steward 补齐强制属性即可晋升，无需批准 → 归位为免审核
        assertThat(rescinded.isRequiresApproval()).isFalse();
        verify(ruleRepository, times(20)).saveAndFlush(any());
    }

    @Test
    @DisplayName("ensureSeed：注册机构不存在时抛 ResourceNotFoundException")
    void ensureSeed_unknownAuthority_throwsNotFound() {
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationProcessService.ensureSeed(raId))
                .isInstanceOf(io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException.class);
    }

    // =========================================================================
    // 2. 流程定义
    // =========================================================================

    @Test
    @DisplayName("getProcess：返回该 RA 的规则视图（含审核配置）；定制规则不被调和改写")
    void getProcess_returnsRuleViews() {
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        RegistrationTransitionRule rule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.AT_LEAST, 2);
        // displayName 非空 = 定制规则：ensureSeed 调和跳过，视图保留定制审核配置
        rule.setDisplayName("评审");
        when(ruleRepository.findByRegistrationAuthorityId(raId)).thenReturn(List.of(rule));

        RegistrationProcessResponse process = registrationProcessService.getProcess(raId);

        assertThat(process.authorityId()).isEqualTo(raId);
        assertThat(process.transitions()).hasSize(1);
        RegistrationProcessResponse.TransitionRuleView view = process.transitions().get(0);
        assertThat(view.from()).isEqualTo(RegistrationStatus.RECORDED);
        assertThat(view.to()).isEqualTo(RegistrationStatus.QUALIFIED);
        assertThat(view.requiresApproval()).isTrue();
        assertThat(view.quorumType()).isEqualTo(QuorumType.AT_LEAST);
        assertThat(view.quorumCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("getCurrentProcess：优先取当前账号活动 Registrar 身份所属 RA")
    void getCurrentProcess_prefersActiveRegistrarAuthority() {
        Registrar registrar = registrar(UUID.randomUUID(), ra);
        when(activeRegistrarService.resolveForCurrentUser()).thenReturn(Optional.of(registrar));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityId(raId)).thenReturn(List.of());

        RegistrationProcessResponse process = registrationProcessService.getCurrentProcess();

        assertThat(process.authorityId()).isEqualTo(raId);
    }

    @Test
    @DisplayName("getCurrentProcess：无活动身份时回退库内首个 RA")
    void getCurrentProcess_fallsBackToFirstAuthority() {
        when(activeRegistrarService.resolveForCurrentUser()).thenReturn(Optional.empty());
        when(registrationAuthorityRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(ra)));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityId(raId)).thenReturn(List.of());

        RegistrationProcessResponse process = registrationProcessService.getCurrentProcess();

        assertThat(process.authorityId()).isEqualTo(raId);
    }

    @Test
    @DisplayName("getCurrentProcess：不存在任何 RA 时抛 1123")
    void getCurrentProcess_noAuthority_throws1123() {
        when(activeRegistrarService.resolveForCurrentUser()).thenReturn(Optional.empty());
        when(registrationAuthorityRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThatThrownBy(() -> registrationProcessService.getCurrentProcess())
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.REGISTRATION_AUTHORITY_NOT_FOUND));
    }

    // =========================================================================
    // 3. 迁移动作视图
    // =========================================================================

    @Test
    @DisplayName("availableTransitions：需审核迁移显示进度，合格 Registrar 可批准、不可执行")
    void availableTransitions_approvalTransition_progressAndPermissions() {
        authenticateAs(AppUserRole.REGISTRAR);
        Registrar me = registrar(UUID.randomUUID(), ra);
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.AT_LEAST, 2);
        RegistrationTransitionRule directRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.RETIRED, false, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndEnabledTrue(
                raId, RegistrationStatus.RECORDED)).thenReturn(List.of(approvalRule, directRule));
        RegistrationTransitionApproval existing = approval(item, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, registrar(UUID.randomUUID(), ra));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                any(), any(), any())).thenReturn(List.of(existing));
        when(activeRegistrarService.resolveActiveRegistrar(accountId)).thenReturn(Optional.of(me));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationLifecycleService.resolveCurrentRole()).thenReturn(AppUserRole.REGISTRAR);
        when(registrationLifecycleService.canExecuteByRole(
                any(), any(), eq(AppUserRole.REGISTRAR))).thenReturn(true);

        List<AvailableTransitionView> views = registrationProcessService.availableTransitions(itemId, accountId);

        assertThat(views).hasSize(2);
        AvailableTransitionView approval = views.stream()
                .filter(v -> v.to() == RegistrationStatus.QUALIFIED).findFirst().orElseThrow();
        assertThat(approval.requiresApproval()).isTrue();
        assertThat(approval.approvedCount()).isEqualTo(1);
        assertThat(approval.quorum()).isEqualTo(2);
        assertThat(approval.canExecute()).isFalse();
        assertThat(approval.canApprove()).isTrue();
        assertThat(approval.approvedByMe()).isFalse();

        AvailableTransitionView direct = views.stream()
                .filter(v -> v.to() == RegistrationStatus.RETIRED).findFirst().orElseThrow();
        assertThat(direct.requiresApproval()).isFalse();
        assertThat(direct.canExecute()).isTrue();
        assertThat(direct.canApprove()).isFalse();
    }

    @Test
    @DisplayName("availableTransitions：已批准过的迁移 approvedByMe=true 且 canApprove=false")
    void availableTransitions_alreadyApproved_grayOut() {
        authenticateAs(AppUserRole.REGISTRAR);
        Registrar me = registrar(UUID.randomUUID(), ra);
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndEnabledTrue(
                raId, RegistrationStatus.RECORDED)).thenReturn(List.of(approvalRule));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                any(), any(), any())).thenReturn(List.of(
                approval(item, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED, me)));
        when(activeRegistrarService.resolveActiveRegistrar(accountId)).thenReturn(Optional.of(me));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        List<AvailableTransitionView> views = registrationProcessService.availableTransitions(itemId, accountId);

        AvailableTransitionView view = views.get(0);
        assertThat(view.approvedByMe()).isTrue();
        assertThat(view.canApprove()).isFalse();
    }

    @Test
    @DisplayName("availableTransitions：VIEWER 只读（不可执行、不可批准）")
    void availableTransitions_viewerReadOnly() {
        authenticateAs(AppUserRole.VIEWER);
        RegistrationTransitionRule directRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.RETIRED, false, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndEnabledTrue(
                raId, RegistrationStatus.RECORDED)).thenReturn(List.of(directRule));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                any(), any(), any())).thenReturn(List.of());
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(activeRegistrarService.resolveActiveRegistrar(accountId)).thenReturn(Optional.empty());
        when(registrationLifecycleService.resolveCurrentRole()).thenReturn(AppUserRole.VIEWER);

        List<AvailableTransitionView> views = registrationProcessService.availableTransitions(itemId, accountId);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).canExecute()).isFalse();
        assertThat(views.get(0).canApprove()).isFalse();
    }

    @Test
    @DisplayName("availableTransitions：无归属 RA 条目回退标准底线；QUALIFIED+ 目标标记需审核且不可执行")
    void availableTransitions_noAuthority_fallsBackToStaticBaseline() {
        authenticateAs(AppUserRole.ADMIN);
        AdministeredItem legacy = item(itemId, RegistrationStatus.RECORDED, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(legacy));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                any(), any(), any())).thenReturn(List.of());
        when(registrationLifecycleService.resolveCurrentRole()).thenReturn(AppUserRole.ADMIN);
        when(registrationLifecycleService.canExecuteByRole(any(), any(), eq(AppUserRole.ADMIN)))
                .thenReturn(true);

        List<AvailableTransitionView> views = registrationProcessService.availableTransitions(itemId, null);

        assertThat(views).extracting(AvailableTransitionView::to)
                .containsExactlyInAnyOrder(RegistrationStatus.QUALIFIED, RegistrationStatus.RETIRED,
                        RegistrationStatus.SUPERSEDED);
        AvailableTransitionView qualified = views.stream()
                .filter(v -> v.to() == RegistrationStatus.QUALIFIED).findFirst().orElseThrow();
        // M1：无 RA 无批准主体（11179-6 4.4 d)），QUALIFIED+ 目标标记需审核且不可直推
        assertThat(qualified.requiresApproval()).isTrue();
        assertThat(qualified.canExecute()).isFalse();
        assertThat(qualified.canApprove()).isFalse();
        AvailableTransitionView retired = views.stream()
                .filter(v -> v.to() == RegistrationStatus.RETIRED).findFirst().orElseThrow();
        assertThat(retired.requiresApproval()).isFalse();
        assertThat(retired.canExecute()).isTrue();
        // SUPERSEDED 为 Recorded 及以上可入的非审核点目标（Annex D.2.5）
        AvailableTransitionView superseded = views.stream()
                .filter(v -> v.to() == RegistrationStatus.SUPERSEDED).findFirst().orElseThrow();
        assertThat(superseded.requiresApproval()).isFalse();
    }

    @Test
    @DisplayName("availableTransitions：无归属 RA 的 CANDIDATE 条目——RECORDED 迁移按 4.4 c) 免审核可直推")
    void availableTransitions_noAuthority_candidateToRecordedDirect() {
        authenticateAs(AppUserRole.ADMIN);
        AdministeredItem legacy = item(itemId, RegistrationStatus.CANDIDATE, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(legacy));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                any(), any(), any())).thenReturn(List.of());
        when(registrationLifecycleService.resolveCurrentRole()).thenReturn(AppUserRole.ADMIN);
        when(registrationLifecycleService.canExecuteByRole(any(), any(), eq(AppUserRole.ADMIN)))
                .thenReturn(true);

        List<AvailableTransitionView> views = registrationProcessService.availableTransitions(itemId, null);

        assertThat(views).extracting(AvailableTransitionView::to)
                .containsExactlyInAnyOrder(RegistrationStatus.RECORDED, RegistrationStatus.HISTORICAL,
                        RegistrationStatus.APPLICATION);
        AvailableTransitionView recorded = views.stream()
                .filter(v -> v.to() == RegistrationStatus.RECORDED).findFirst().orElseThrow();
        // 4.4 c)：submitter/steward 补齐强制属性即可晋升，无需批准——直推开放
        assertThat(recorded.requiresApproval()).isFalse();
        assertThat(recorded.canExecute()).isTrue();
        assertThat(recorded.canApprove()).isFalse();
        assertThat(views.stream().filter(v -> v.to() == RegistrationStatus.HISTORICAL).findFirst().orElseThrow()
                .canExecute()).isTrue();
    }

    @Test
    @DisplayName("availableTransitions：QUALIFIED+ 降级目标（D.4.2/D.4.3/D.2.9 审核点）标记需审核且不可执行")
    void availableTransitions_qualifiedDowngrades_requireApproval() {
        authenticateAs(AppUserRole.REGISTRAR);
        Registrar me = registrar(UUID.randomUUID(), ra);
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(registrationLifecycleService.resolveCurrentRole()).thenReturn(AppUserRole.REGISTRAR);
        when(activeRegistrarService.resolveActiveRegistrar(accountId)).thenReturn(Optional.of(me));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                any(), any(), any())).thenReturn(List.of());

        // QUALIFIED 源状态：STANDARD 为 4.4 d) 审核点；RETIRED/SUPERSEDED 为 D.4.2/D.4.3 审核点
        AdministeredItem qualified = item(itemId, RegistrationStatus.QUALIFIED, ra);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(qualified));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndEnabledTrue(
                raId, RegistrationStatus.QUALIFIED)).thenReturn(List.of(
                rule(ra, RegistrationStatus.QUALIFIED, RegistrationStatus.STANDARD, true,
                        ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null),
                rule(ra, RegistrationStatus.QUALIFIED, RegistrationStatus.RETIRED, true,
                        ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null),
                rule(ra, RegistrationStatus.QUALIFIED, RegistrationStatus.SUPERSEDED, true,
                        ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null)));

        List<AvailableTransitionView> qualifiedViews =
                registrationProcessService.availableTransitions(itemId, accountId);

        assertThat(qualifiedViews).hasSize(3);
        assertThat(qualifiedViews).allSatisfy(v -> {
            assertThat(v.requiresApproval()).isTrue();
            assertThat(v.canExecute()).isFalse();
        });
        // 合格 Registrar 对审核点可批准
        assertThat(qualifiedViews).allSatisfy(v -> assertThat(v.canApprove()).isTrue());

        // SUPERSEDED 源状态：RETIRED 为 D.2.9 审核点
        AdministeredItem superseded = item(itemId, RegistrationStatus.SUPERSEDED, ra);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(superseded));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndEnabledTrue(
                raId, RegistrationStatus.SUPERSEDED)).thenReturn(List.of(
                rule(ra, RegistrationStatus.SUPERSEDED, RegistrationStatus.RETIRED, true,
                        ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null)));

        List<AvailableTransitionView> supersededViews =
                registrationProcessService.availableTransitions(itemId, accountId);

        assertThat(supersededViews).hasSize(1);
        assertThat(supersededViews.get(0).to()).isEqualTo(RegistrationStatus.RETIRED);
        assertThat(supersededViews.get(0).requiresApproval()).isTrue();
        assertThat(supersededViews.get(0).canExecute()).isFalse();
    }

    // =========================================================================
    // 4. 执行（execute）
    // =========================================================================

    @Test
    @DisplayName("execute：无需审核迁移委托生命周期服务并清除陈旧审批")
    void execute_directTransition_delegatesAndClearsApprovals() {
        RegistrationTransitionRule directRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.RETIRED, false, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.RECORDED, RegistrationStatus.RETIRED))
                .thenReturn(Optional.of(directRule));
        when(registrationLifecycleService.transition(itemId, RegistrationStatus.RETIRED, "退役"))
                .thenReturn(item);

        AdministeredItem result = registrationProcessService.execute(
                itemId, RegistrationStatus.RETIRED, "退役", accountId);

        assertThat(result).isSameAs(item);
        verify(registrationLifecycleService).transition(itemId, RegistrationStatus.RETIRED, "退役");
        verify(approvalRepository).deleteByAdministeredItemId(itemId);
    }

    @Test
    @DisplayName("execute：需审核迁移拒绝（1120）")
    void execute_requiresApprovalTransition_rejected() {
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED))
                .thenReturn(Optional.of(approvalRule));

        assertThatThrownBy(() -> registrationProcessService.execute(
                itemId, RegistrationStatus.QUALIFIED, null, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.TRANSITION_REQUIRES_APPROVAL));
        verify(registrationLifecycleService, never()).transition(any(), any(), anyString());
    }

    @Test
    @DisplayName("execute：规则表外迁移由生命周期校验抛出非法迁移")
    void execute_ruleMissing_rejectedByLifecycleValidation() {
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.RECORDED, RegistrationStatus.CANDIDATE))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new BusinessException(
                        ApiCodes.INVALID_STATE_TRANSITION_FROM_RECORDED, "非法的状态迁移"))
                .when(registrationLifecycleService)
                .validateTransition(RegistrationStatus.RECORDED, RegistrationStatus.CANDIDATE, raId);

        assertThatThrownBy(() -> registrationProcessService.execute(
                itemId, RegistrationStatus.CANDIDATE, null, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.INVALID_STATE_TRANSITION_FROM_RECORDED));
    }

    // =========================================================================
    // 5. 批准（approve）
    // =========================================================================

    @Test
    @DisplayName("approve：AT_LEAST 2 时第一名批准不生效，记录审批并返回进度")
    void approve_belowQuorum_recordsApprovalNotExecuted() {
        authenticateAs(AppUserRole.REGISTRAR);
        Registrar me = registrar(UUID.randomUUID(), ra);
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.AT_LEAST, 2);
        stubApprovePreconditions(approvalRule, me);
        when(approvalRepository.countByAdministeredItemIdAndFromStatusAndToStatus(
                itemId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED)).thenReturn(1L);
        // 批准后的最新视图：无其他启用规则、无审批记录
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndEnabledTrue(
                raId, RegistrationStatus.RECORDED)).thenReturn(List.of(approvalRule));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                any(), any(), any())).thenReturn(List.of());

        ApprovalResultResponse result = registrationProcessService.approve(
                itemId, RegistrationStatus.QUALIFIED, accountId);

        assertThat(result.executed()).isFalse();
        ArgumentCaptor<RegistrationTransitionApproval> captor =
                ArgumentCaptor.forClass(RegistrationTransitionApproval.class);
        verify(approvalRepository).save(captor.capture());
        RegistrationTransitionApproval saved = captor.getValue();
        assertThat(saved.getAdministeredItem()).isSameAs(item);
        assertThat(saved.getFromStatus()).isEqualTo(RegistrationStatus.RECORDED);
        assertThat(saved.getToStatus()).isEqualTo(RegistrationStatus.QUALIFIED);
        assertThat(saved.getRegistrar()).isSameAs(me);
        assertThat(saved.getAccountId()).isEqualTo(accountId);
        assertThat(saved.getApprovedAt()).isNotNull();
        verify(registrationLifecycleService, never()).applyTransition(any(), any(), anyString());
    }

    @Test
    @DisplayName("approve：达到 quorum 时同一事务生效、清除该项全部审批并返回最新视图")
    void approve_reachingQuorum_executesAndClearsApprovals() {
        authenticateAs(AppUserRole.REGISTRAR);
        Registrar me = registrar(UUID.randomUUID(), ra);
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.AT_LEAST, 2);
        stubApprovePreconditions(approvalRule, me);
        when(approvalRepository.countByAdministeredItemIdAndFromStatusAndToStatus(
                itemId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED)).thenReturn(2L);
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndEnabledTrue(
                raId, RegistrationStatus.RECORDED)).thenReturn(List.of(approvalRule));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                any(), any(), any())).thenReturn(List.of());

        ApprovalResultResponse result = registrationProcessService.approve(
                itemId, RegistrationStatus.QUALIFIED, accountId);

        assertThat(result.executed()).isTrue();
        verify(approvalRepository).deleteByAdministeredItemId(itemId);
        ArgumentCaptor<String> noteCaptor = ArgumentCaptor.forClass(String.class);
        verify(registrationLifecycleService).applyTransition(
                eq(itemId), eq(RegistrationStatus.QUALIFIED), noteCaptor.capture());
        assertThat(noteCaptor.getValue()).contains("批准生效");
        assertThat(result.transitions()).isNotNull();
    }

    @Test
    @DisplayName("approve：ANY_ONE 规则下首名批准即生效")
    void approve_anyOne_firstApprovalExecutes() {
        authenticateAs(AppUserRole.REGISTRAR);
        Registrar me = registrar(UUID.randomUUID(), ra);
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        stubApprovePreconditions(approvalRule, me);
        when(approvalRepository.countByAdministeredItemIdAndFromStatusAndToStatus(
                itemId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED)).thenReturn(1L);
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndEnabledTrue(
                raId, RegistrationStatus.RECORDED)).thenReturn(List.of(approvalRule));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                any(), any(), any())).thenReturn(List.of());

        ApprovalResultResponse result = registrationProcessService.approve(
                itemId, RegistrationStatus.QUALIFIED, accountId);

        assertThat(result.executed()).isTrue();
        verify(registrationLifecycleService).applyTransition(any(), any(), anyString());
    }

    @Test
    @DisplayName("approve：QUALIFIED→RETIRED（D.4.2 审核点）——合格 Registrar 批准即生效")
    void approve_qualifiedToRetired_executes() {
        authenticateAs(AppUserRole.REGISTRAR);
        Registrar me = registrar(UUID.randomUUID(), ra);
        AdministeredItem qualified = item(itemId, RegistrationStatus.QUALIFIED, ra);
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.QUALIFIED,
                RegistrationStatus.RETIRED, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(qualified));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.QUALIFIED, RegistrationStatus.RETIRED))
                .thenReturn(Optional.of(approvalRule));
        when(activeRegistrarService.resolveActiveRegistrar(accountId)).thenReturn(Optional.of(me));
        when(approvalRepository.existsByAdministeredItemIdAndFromStatusAndToStatusAndRegistrarId(
                any(), any(), any(), any())).thenReturn(false);
        when(approvalRepository.countByAdministeredItemIdAndFromStatusAndToStatus(
                itemId, RegistrationStatus.QUALIFIED, RegistrationStatus.RETIRED)).thenReturn(1L);
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndEnabledTrue(
                raId, RegistrationStatus.QUALIFIED)).thenReturn(List.of(approvalRule));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                any(), any(), any())).thenReturn(List.of());

        ApprovalResultResponse result = registrationProcessService.approve(
                itemId, RegistrationStatus.RETIRED, accountId);

        assertThat(result.executed()).isTrue();
        verify(approvalRepository).deleteByAdministeredItemId(itemId);
        verify(registrationLifecycleService).applyTransition(
                eq(itemId), eq(RegistrationStatus.RETIRED), contains("批准生效"));
    }

    @Test
    @DisplayName("approve：重复批准被拒绝（1122）")
    void approve_duplicateRejected() {
        Registrar me = registrar(UUID.randomUUID(), ra);
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        stubApprovePreconditions(approvalRule, me);
        when(approvalRepository.existsByAdministeredItemIdAndFromStatusAndToStatusAndRegistrarId(
                itemId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED, me.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> registrationProcessService.approve(
                itemId, RegistrationStatus.QUALIFIED, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.DUPLICATE_APPROVAL));
        verify(approvalRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve：无需审核的迁移误用批准返回 1121")
    void approve_nonApprovalTransition_rejected() {
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        RegistrationTransitionRule directRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.RETIRED, false, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.RECORDED, RegistrationStatus.RETIRED))
                .thenReturn(Optional.of(directRule));

        assertThatThrownBy(() -> registrationProcessService.approve(
                itemId, RegistrationStatus.RETIRED, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.TRANSITION_APPROVAL_NOT_REQUIRED));
    }

    @Test
    @DisplayName("approve：非该项 RA 的 Registrar 身份拒绝（403）")
    void approve_foreignRegistrar_forbidden() {
        RegistrationAuthority otherRa = authority(UUID.randomUUID());
        Registrar foreign = registrar(UUID.randomUUID(), otherRa);
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED))
                .thenReturn(Optional.of(approvalRule));
        when(activeRegistrarService.resolveActiveRegistrar(accountId))
                .thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> registrationProcessService.approve(
                itemId, RegistrationStatus.QUALIFIED, accountId))
                .isInstanceOf(AccessDeniedException.class);
        verify(approvalRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve：SELECTED_REGISTRARS 时集合外身份拒绝、集合内可批准")
    void approve_selectedRegistrars_onlySetMembers() {
        authenticateAs(AppUserRole.REGISTRAR);
        Registrar inSet = registrar(UUID.randomUUID(), ra);
        Registrar outOfSet = registrar(UUID.randomUUID(), ra);
        RegistrationTransitionRule selectedRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.SELECTED_REGISTRARS, QuorumType.ANY_ONE, null);
        selectedRule.getApproverRegistrarIds().add(inSet.getId());
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED))
                .thenReturn(Optional.of(selectedRule));
        when(approvalRepository.existsByAdministeredItemIdAndFromStatusAndToStatusAndRegistrarId(
                any(), any(), any(), any())).thenReturn(false);
        when(activeRegistrarService.resolveActiveRegistrar(accountId))
                .thenReturn(Optional.of(outOfSet));

        assertThatThrownBy(() -> registrationProcessService.approve(
                itemId, RegistrationStatus.QUALIFIED, accountId))
                .isInstanceOf(AccessDeniedException.class);

        when(activeRegistrarService.resolveActiveRegistrar(accountId))
                .thenReturn(Optional.of(inSet));
        when(approvalRepository.countByAdministeredItemIdAndFromStatusAndToStatus(
                any(), any(), any())).thenReturn(1L);
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndEnabledTrue(
                raId, RegistrationStatus.RECORDED)).thenReturn(List.of(selectedRule));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                any(), any(), any())).thenReturn(List.of());

        ApprovalResultResponse result = registrationProcessService.approve(
                itemId, RegistrationStatus.QUALIFIED, accountId);

        assertThat(result.executed()).isTrue();
    }

    @Test
    @DisplayName("approve：生效时强制校验失败则异常传播（整个事务回滚）")
    void approve_effectValidationFailure_rollsBack() {
        Registrar me = registrar(UUID.randomUUID(), ra);
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        stubApprovePreconditions(approvalRule, me);
        when(approvalRepository.countByAdministeredItemIdAndFromStatusAndToStatus(
                itemId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED)).thenReturn(1L);
        org.mockito.Mockito.doThrow(new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "Measure_Class 必须关联至少一个 Unit_of_Measure"))
                .when(registrationLifecycleService)
                .applyTransition(itemId, RegistrationStatus.QUALIFIED,
                        "批准生效：RECORDED → QUALIFIED（1 人批准）");

        assertThatThrownBy(() -> registrationProcessService.approve(
                itemId, RegistrationStatus.QUALIFIED, accountId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unit_of_Measure");
    }


    @Test
    @DisplayName("execute：无归属 RA 条目推进 QUALIFIED+ 拒绝 1120（无批准主体）")
    void execute_noAuthorityQualifiedPlus_rejected() {
        AdministeredItem legacy = item(itemId, RegistrationStatus.RECORDED, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> registrationProcessService.execute(
                itemId, RegistrationStatus.QUALIFIED, null, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.TRANSITION_REQUIRES_APPROVAL);
                    assertThat(be.getMessage()).contains("未归属注册机构");
                });
        verify(registrationLifecycleService, never()).transition(any(), any(), anyString());
    }

    @Test
    @DisplayName("execute：无归属 RA 条目 CANDIDATE→RECORDED 按 4.4 c) 免审核直推放行")
    void execute_noAuthorityCandidateToRecord_allowed() {
        AdministeredItem legacy = item(itemId, RegistrationStatus.CANDIDATE, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(legacy));
        when(registrationLifecycleService.transition(itemId, RegistrationStatus.RECORDED, "登记"))
                .thenReturn(legacy);

        AdministeredItem result = registrationProcessService.execute(
                itemId, RegistrationStatus.RECORDED, "登记", accountId);

        assertThat(result).isSameAs(legacy);
        verify(registrationLifecycleService).transition(itemId, RegistrationStatus.RECORDED, "登记");
    }

    @Test
    @DisplayName("execute：无归属 RA 条目的非审核点目标（RETIRED）仍放行")
    void execute_noAuthorityNonApprovalTarget_allowed() {
        AdministeredItem legacy = item(itemId, RegistrationStatus.RECORDED, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(legacy));
        when(registrationLifecycleService.transition(itemId, RegistrationStatus.RETIRED, "退役"))
                .thenReturn(legacy);

        AdministeredItem result = registrationProcessService.execute(
                itemId, RegistrationStatus.RETIRED, "退役", accountId);

        assertThat(result).isSameAs(legacy);
        verify(registrationLifecycleService).transition(itemId, RegistrationStatus.RETIRED, "退役");
    }

    @Test
    @DisplayName("execute：无归属 RA 条目 QUALIFIED→RETIRED（D.4.2 审核点）拒绝 1120")
    void execute_noAuthorityQualifiedToRetired_rejected() {
        AdministeredItem legacy = item(itemId, RegistrationStatus.QUALIFIED, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> registrationProcessService.execute(
                itemId, RegistrationStatus.RETIRED, null, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.TRANSITION_REQUIRES_APPROVAL);
                    assertThat(be.getMessage()).contains("未归属注册机构");
                });
        verify(registrationLifecycleService, never()).transition(any(), any(), anyString());
    }

    // =========================================================================
    // 7. 管理状态操作（reject / request-qualification）
    // =========================================================================

    @Test
    @DisplayName("reject：成功后 REJECTED + 批准作废 + 注册状态不变")
    void reject_success_setsRejectedAndDeletesApprovals() {
        Registrar me = registrar(UUID.randomUUID(), ra);
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        item.getRegistrationState().setAdministrativeStatus(AdministrativeStatus.PROVISIONALLY_QUALIFIED);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED))
                .thenReturn(Optional.of(approvalRule));
        when(activeRegistrarService.resolveActiveRegistrar(accountId)).thenReturn(Optional.of(me));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                itemId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED))
                .thenReturn(List.of(approval(item, RegistrationStatus.RECORDED,
                        RegistrationStatus.QUALIFIED, registrar(UUID.randomUUID(), ra))));
        when(administeredItemRepository.save(item)).thenReturn(item);

        AdministeredItem result = registrationProcessService.reject(itemId, "信息不足", accountId);

        assertThat(result.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.RECORDED);
        verify(administrativeStatusService).setRejected(item);
        verify(approvalRepository).deleteByAdministeredItemIdAndFromStatusAndToStatus(
                itemId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED);
    }

    @Test
    @DisplayName("reject：管理状态非 PROVISIONALLY_QUALIFIED 抛 1132")
    void reject_notQualifiedStatus_throws1132() {
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> registrationProcessService.reject(itemId, null, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.INVALID_ADMINISTRATIVE_STATUS_FOR_REJECTION));
    }

    @Test
    @DisplayName("reject：无未生效批准记录抛 1133")
    void reject_noPendingApprovals_throws1133() {
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        item.getRegistrationState().setAdministrativeStatus(AdministrativeStatus.PROVISIONALLY_QUALIFIED);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED))
                .thenReturn(Optional.of(approvalRule));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                itemId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> registrationProcessService.reject(itemId, null, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.NO_PENDING_QUALIFICATION_APPLICATION));
    }

    @Test
    @DisplayName("reject：非审核集合内 Registrar 拒绝（403）")
    void reject_unqualifiedRegistrar_forbidden() {
        RegistrationAuthority otherRa = authority(UUID.randomUUID());
        Registrar foreign = registrar(UUID.randomUUID(), otherRa);
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        item.getRegistrationState().setAdministrativeStatus(AdministrativeStatus.PROVISIONALLY_QUALIFIED);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED))
                .thenReturn(Optional.of(approvalRule));
        when(activeRegistrarService.resolveActiveRegistrar(accountId))
                .thenReturn(Optional.of(foreign));
        when(approvalRepository.findByAdministeredItemIdAndFromStatusAndToStatus(
                itemId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED))
                .thenReturn(List.of(approval(item, RegistrationStatus.RECORDED,
                        RegistrationStatus.QUALIFIED, registrar(UUID.randomUUID(), ra))));

        assertThatThrownBy(() -> registrationProcessService.reject(itemId, null, accountId))
                .isInstanceOf(AccessDeniedException.class);
        verify(administrativeStatusService, never()).setRejected(any());
    }

    @Test
    @DisplayName("request-qualification：成功置 PROVISIONALLY_QUALIFIED")
    void requestQualification_success_setsProvisionallyQualified() {
        item = item(itemId, RegistrationStatus.RECORDED, ra);
        Contact steward = contact(UUID.randomUUID());
        item.setStewardshipRecord(stewardshipRecord(steward));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAdministrationService.isItemStewardedByAccount(item, accountId)).thenReturn(true);
        when(administeredItemRepository.save(item)).thenReturn(item);

        AdministeredItem result = registrationProcessService.requestQualification(itemId, accountId);

        assertThat(result.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.RECORDED);
        verify(administrativeStatusService).setProvisionallyQualified(item);
    }

    @Test
    @DisplayName("request-qualification：非主管拒绝")
    void requestQualification_notSteward_forbidden() {
        item = item(itemId, RegistrationStatus.RECORDED, ra);
        Contact steward = contact(UUID.randomUUID());
        item.setStewardshipRecord(stewardshipRecord(steward));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAdministrationService.isItemStewardedByAccount(item, accountId)).thenReturn(false);

        assertThatThrownBy(() -> registrationProcessService.requestQualification(itemId, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.NOT_ITEM_STEWARD));
        verify(administrativeStatusService, never()).setProvisionallyQualified(any());
    }

    @Test
    @DisplayName("request-qualification：非 RECORDED 状态拒绝")
    void requestQualification_notRecorded_throws() {
        AdministeredItem qualified = item(itemId, RegistrationStatus.QUALIFIED, ra);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(qualified));

        assertThatThrownBy(() -> registrationProcessService.requestQualification(itemId, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.INVALID_STATE_TRANSITION_FROM_QUALIFIED));
    }

    @Test
    @DisplayName("request-qualification：已是 PROVISIONALLY_QUALIFIED 抛 1137")
    void requestQualification_alreadyProvisionallyQualified_throws1137() {
        item = item(itemId, RegistrationStatus.RECORDED, ra);
        item.getRegistrationState().setAdministrativeStatus(AdministrativeStatus.PROVISIONALLY_QUALIFIED);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> registrationProcessService.requestQualification(itemId, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.ALREADY_PROVISIONALLY_QUALIFIED));
    }

    // =========================================================================
    // 8. Submitter 白名单（INCOMPLETE→CANDIDATE）
    // =========================================================================

    @Test
    @DisplayName("execute：归属 Submitter 执行 INCOMPLETE→CANDIDATE 成功")
    void execute_submitterOwnedIncompleteToCandidate_success() {
        AdministeredItem incomplete = item(itemId, RegistrationStatus.INCOMPLETE, ra);
        incomplete.setStewardshipRecord(stewardshipRecord(contact(UUID.randomUUID())));
        RegistrationTransitionRule directRule = rule(ra, RegistrationStatus.INCOMPLETE,
                RegistrationStatus.CANDIDATE, false, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(incomplete));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.INCOMPLETE, RegistrationStatus.CANDIDATE))
                .thenReturn(Optional.of(directRule));
        when(activeRegistrarService.isSubmitter(accountId)).thenReturn(true);
        when(itemAdministrationService.isItemOwnedByAccount(incomplete, accountId)).thenReturn(true);
        when(registrationLifecycleService.applyTransition(itemId, RegistrationStatus.CANDIDATE, "成为候选"))
                .thenReturn(incomplete);

        AdministeredItem result = registrationProcessService.execute(
                itemId, RegistrationStatus.CANDIDATE, "成为候选", accountId);

        assertThat(result).isSameAs(incomplete);
        verify(registrationLifecycleService).applyTransition(itemId, RegistrationStatus.CANDIDATE, "成为候选");
        verify(registrationLifecycleService, never()).transition(any(), any(), anyString());
    }

    @Test
    @DisplayName("execute：非归属 Submitter 执行 INCOMPLETE→CANDIDATE 拒绝")
    void execute_submitterNotOwned_forbidden() {
        AdministeredItem incomplete = item(itemId, RegistrationStatus.INCOMPLETE, ra);
        incomplete.setStewardshipRecord(stewardshipRecord(contact(UUID.randomUUID())));
        RegistrationTransitionRule directRule = rule(ra, RegistrationStatus.INCOMPLETE,
                RegistrationStatus.CANDIDATE, false, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(incomplete));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.INCOMPLETE, RegistrationStatus.CANDIDATE))
                .thenReturn(Optional.of(directRule));
        when(activeRegistrarService.isSubmitter(accountId)).thenReturn(true);
        when(itemAdministrationService.isItemOwnedByAccount(incomplete, accountId)).thenReturn(false);
        doThrow(new BusinessException(ApiCodes.UNAUTHORIZED_STATE_TRANSITION, "无权"))
                .when(registrationLifecycleService)
                .transition(itemId, RegistrationStatus.CANDIDATE, null);

        assertThatThrownBy(() -> registrationProcessService.execute(
                itemId, RegistrationStatus.CANDIDATE, null, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.UNAUTHORIZED_STATE_TRANSITION));
    }

    @Test
    @DisplayName("execute：归属 Submitter 但缺少 stewardshipRecord 拒绝（Candidate 最低需求）")
    void execute_submitterMissingStewardship_throws1135() {
        AdministeredItem incomplete = item(itemId, RegistrationStatus.INCOMPLETE, ra);
        RegistrationTransitionRule directRule = rule(ra, RegistrationStatus.INCOMPLETE,
                RegistrationStatus.CANDIDATE, false, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(incomplete));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.INCOMPLETE, RegistrationStatus.CANDIDATE))
                .thenReturn(Optional.of(directRule));
        when(activeRegistrarService.isSubmitter(accountId)).thenReturn(true);

        assertThatThrownBy(() -> registrationProcessService.execute(
                itemId, RegistrationStatus.CANDIDATE, null, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.CANDIDATE_MINIMUM_REQUIREMENT_NOT_MET));
    }

    @Test
    @DisplayName("execute：SUBMITTER 执行其他迁移仍被拒绝")
    void execute_submitterOtherTransition_stillDenied() {
        AdministeredItem incomplete = item(itemId, RegistrationStatus.INCOMPLETE, ra);
        incomplete.setStewardshipRecord(stewardshipRecord(contact(UUID.randomUUID())));
        RegistrationTransitionRule directRule = rule(ra, RegistrationStatus.INCOMPLETE,
                RegistrationStatus.HISTORICAL, false, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(incomplete));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, RegistrationStatus.INCOMPLETE, RegistrationStatus.HISTORICAL))
                .thenReturn(Optional.of(directRule));
        doThrow(new BusinessException(ApiCodes.UNAUTHORIZED_STATE_TRANSITION, "无权"))
                .when(registrationLifecycleService)
                .transition(itemId, RegistrationStatus.HISTORICAL, null);

        assertThatThrownBy(() -> registrationProcessService.execute(
                itemId, RegistrationStatus.HISTORICAL, null, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.UNAUTHORIZED_STATE_TRANSITION));
    }

    // =========================================================================
    // 6. 并发与数据完整性（M3）
    // =========================================================================

    @Test
    @DisplayName("approve：并发批准撞唯一约束映射为重复批准（1122）")
    void approve_concurrentDuplicateConstraint_mappedTo1122() {
        Registrar me = registrar(UUID.randomUUID(), ra);
        RegistrationTransitionRule approvalRule = rule(ra, RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE, null);
        stubApprovePreconditions(approvalRule, me);
        when(approvalRepository.save(any(RegistrationTransitionApproval.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "uk_rta_item_from_to_registrar"));

        assertThatThrownBy(() -> registrationProcessService.approve(
                itemId, RegistrationStatus.QUALIFIED, accountId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.DUPLICATE_APPROVAL));
    }

    @Test
    @DisplayName("ensureSeed：并发播种撞唯一约束按已播种幂等跳过，不影响其余规则")
    void ensureSeed_constraintViolation_treatedAsSeeded() {
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.existsByRegistrationAuthorityIdAndFromStatusAndToStatus(
                eq(raId), any(), any())).thenReturn(false);
        List<RegistrationTransitionRule> saved = new ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        when(ruleRepository.saveAndFlush(any(RegistrationTransitionRule.class)))
                .thenAnswer(inv -> {
                    // 模拟并发播种：第一条规则已被另一事务抢先插入，其余正常
                    if (calls.getAndIncrement() == 0) {
                        throw new org.springframework.dao.DataIntegrityViolationException(
                                "uk_rtr_authority_from_to");
                    }
                    RegistrationTransitionRule rule = inv.getArgument(0);
                    saved.add(rule);
                    return rule;
                });

        registrationProcessService.ensureSeed(raId);

        // 冲突规则被跳过，其余 19 条照常播种（20 条标准集 - 1 条冲突）
        assertThat(saved).hasSize(19);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** 模拟播种持久化：exists 检查 + save 记录到内存列表（第二次播种即全部已存在）。 */
    private List<RegistrationTransitionRule> stubSeedPersistence() {
        List<RegistrationTransitionRule> saved = new ArrayList<>();
        Set<String> seededKeys = new HashSet<>();
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.existsByRegistrationAuthorityIdAndFromStatusAndToStatus(
                eq(raId), any(), any()))
                .thenAnswer(inv -> !seededKeys.add(key(inv.getArgument(1), inv.getArgument(2))));
        when(ruleRepository.saveAndFlush(any(RegistrationTransitionRule.class)))
                .thenAnswer(inv -> {
                    RegistrationTransitionRule rule = inv.getArgument(0);
                    saved.add(rule);
                    return rule;
                });
        return saved;
    }

    /** approve 前置桩：规则、注册项、资格（exists 不重复）齐备；生效与否由调用方控制 count。 */
    private void stubApprovePreconditions(RegistrationTransitionRule approvalRule, Registrar me) {
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(ra));
        when(ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                raId, approvalRule.getFromStatus(), approvalRule.getToStatus()))
                .thenReturn(Optional.of(approvalRule));
        when(activeRegistrarService.resolveActiveRegistrar(accountId)).thenReturn(Optional.of(me));
        when(approvalRepository.existsByAdministeredItemIdAndFromStatusAndToStatusAndRegistrarId(
                any(), any(), any(), any())).thenReturn(false);
    }

    private static String key(RegistrationStatus from, RegistrationStatus to) {
        return from + "→" + to;
    }

    private static Contact contact(UUID id) {
        Contact contact = new Contact();
        contact.setId(id);
        return contact;
    }

    private static StewardshipRecord stewardshipRecord(Contact contact) {
        StewardshipRecord record = new StewardshipRecord();
        record.setContact(contact);
        return record;
    }

    private static RegistrationAuthority authority(UUID id) {
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(id);
        return ra;
    }

    private static Registrar registrar(UUID id, RegistrationAuthority ra) {
        Registrar registrar = new Registrar();
        registrar.setId(id);
        registrar.setRegistrationAuthority(ra);
        return registrar;
    }

    private static AdministeredItem item(UUID id, RegistrationStatus status, RegistrationAuthority ra) {
        AdministeredItem ai = new AdministeredItem();
        ai.setCreationDateTime(LocalDateTime.now());
        ai.setId(id);
        ai.setRegistrationAuthority(ra);
        RegistrationState state = new RegistrationState();
        state.setId(UUID.randomUUID());
        state.setRegistrationStatus(status);
        state.setEffectiveDate(LocalDateTime.now());
        ai.setRegistrationState(state);
        ai.setItem(new TestItem());
        return ai;
    }

    private static RegistrationTransitionRule rule(RegistrationAuthority ra,
                                                   RegistrationStatus from, RegistrationStatus to,
                                                   boolean requiresApproval,
                                                   ApproverMode approverMode,
                                                   QuorumType quorumType,
                                                   Integer quorumCount) {
        RegistrationTransitionRule rule = new RegistrationTransitionRule();
        rule.setRegistrationAuthority(ra);
        rule.setFromStatus(from);
        rule.setToStatus(to);
        rule.setRequiresApproval(requiresApproval);
        rule.setApproverMode(approverMode);
        rule.setQuorumType(quorumType);
        rule.setQuorumCount(quorumCount);
        rule.setEnabled(true);
        return rule;
    }

    private static RegistrationTransitionApproval approval(AdministeredItem item,
                                                           RegistrationStatus from,
                                                           RegistrationStatus to,
                                                           Registrar registrar) {
        RegistrationTransitionApproval approval = new RegistrationTransitionApproval();
        approval.setAdministeredItem(item);
        approval.setFromStatus(from);
        approval.setToStatus(to);
        approval.setRegistrar(registrar);
        approval.setApprovedAt(LocalDateTime.now());
        return approval;
    }

    private void authenticateAs(AppUserRole role) {
        AuthenticatedUser user = new AuthenticatedUser(accountId, "testuser",
                role == AppUserRole.ADMIN ? SystemRole.SUPER_ADMIN : null);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority(role.authority())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** 使 ai.getItem() 可用的最小 Item 子类。 */
    private static class TestItem extends Item {
    }
}
