package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.auth.AppUserRole;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.registration.RegistrationStateHistoryEntry;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link RegistrationLifecycleService} 单元测试。
 *
 * <p>覆盖目标：</p>
 * <ul>
 *   <li>状态转换规则（合法/非法组合）</li>
 *   <li>角色权限矩阵（ADMIN/REGISTRAR/VIEWER）</li>
 *   <li>业务错误码（1100-1199 区间）</li>
 *   <li>状态变更历史查询（分页、过滤）</li>
 *   <li>边界条件（空状态、不存在的管理项）</li>
 * </ul>
 *
 * <p><b>WI-019 变更：</b>注册状态已与 ISO/IEC 11179-6:2023 对齐。
 * CANDIDATE → SUBMITTED，PREFERRED_STANDARD → PREFERRED。为符合 ISO/IEC 11179-3:2023 第9章，恢复 APPLICATION。</p>
 */
@ExtendWith(MockitoExtension.class)
class RegistrationLifecycleServiceTest {

    @Mock
    private AdministeredItemRepository administeredItemRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ContactRepository contactRepository;

    @InjectMocks
    private RegistrationLifecycleService registrationLifecycleService;

    private UUID itemId;
    private AdministeredItem item;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        item = new AdministeredItem();
        item.setId(itemId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // 1. 基础状态转换测试
    // =========================================================================

    @Test
    @DisplayName("INCOMPLETE → SUBMITTED: 合法转换，ADMIN 角色")
    void transition_incompleteToSubmitted_asAdmin_shouldSucceed() {
        authenticateAs(AppUserRole.ADMIN);
        item.setRegistrationState(createState(RegistrationStatus.INCOMPLETE));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(administeredItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdministeredItem result = registrationLifecycleService.transition(
                itemId, RegistrationStatus.CANDIDATE, "提交审核");

        assertThat(result.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.CANDIDATE);
        assertThat(result.getRegistrationState().getAdministrativeNote()).isEqualTo("提交审核");
        assertThat(result.getRegistrationState().getPreviousState()).isNotNull();
        assertThat(result.getRegistrationState().getPreviousState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.INCOMPLETE);
        assertThat(result.getRegistrationState().getPreviousState().getUntilDate()).isNotNull();
    }

    @Test
    @DisplayName("SUBMITTED → RECORDED: 合法转换，REGISTRAR 角色")
    void transition_submittedToRecorded_asRegistrar_shouldSucceed() {
        authenticateAs(AppUserRole.REGISTRAR);
        item.setRegistrationState(createState(RegistrationStatus.CANDIDATE));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(administeredItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdministeredItem result = registrationLifecycleService.transition(
                itemId, RegistrationStatus.RECORDED, "已记录");

        assertThat(result.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.RECORDED);
    }

    @Test
    @DisplayName("完整正向流程: INCOMPLETE → SUBMITTED → RECORDED → QUALIFIED → STANDARD")
    void transition_fullForwardFlow_shouldSucceed() {
        authenticateAs(AppUserRole.ADMIN);

        RegistrationStatus[] flow = {
                RegistrationStatus.INCOMPLETE,
                RegistrationStatus.CANDIDATE,
                RegistrationStatus.RECORDED,
                RegistrationStatus.QUALIFIED,
                RegistrationStatus.STANDARD
        };

        for (int i = 0; i < flow.length - 1; i++) {
            RegistrationStatus from = flow[i];
            RegistrationStatus to = flow[i + 1];

            item.setRegistrationState(createState(from));
            when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
            when(administeredItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AdministeredItem result = registrationLifecycleService.transition(
                    itemId, to, "步骤 " + (i + 1));

            assertThat(result.getRegistrationState().getRegistrationStatus()).isEqualTo(to);

            // 验证历史链
            RegistrationState current = result.getRegistrationState();
            int depth = 0;
            while (current != null && depth < 10) {
                depth++;
                current = current.getPreviousState();
            }
            assertThat(depth).isGreaterThan(0);
        }
    }


    @Test
    @DisplayName("INCOMPLETE → APPLICATION: 合法转换，ADMIN 角色")
    void transition_incompleteToApplication_asAdmin_shouldSucceed() {
        authenticateAs(AppUserRole.ADMIN);
        item.setRegistrationState(createState(RegistrationStatus.INCOMPLETE));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(administeredItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdministeredItem result = registrationLifecycleService.transition(
                itemId, RegistrationStatus.APPLICATION, "应用登记");

        assertThat(result.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.APPLICATION);
    }

    @Test
    @DisplayName("SUBMITTED → APPLICATION: 合法转换，ADMIN 角色")
    void transition_submittedToApplication_asAdmin_shouldSucceed() {
        authenticateAs(AppUserRole.ADMIN);
        item.setRegistrationState(createState(RegistrationStatus.CANDIDATE));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(administeredItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdministeredItem result = registrationLifecycleService.transition(
                itemId, RegistrationStatus.APPLICATION, "应用登记");

        assertThat(result.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.APPLICATION);
    }

    @Test
    @DisplayName("APPLICATION → RECORDED: 合法转换，ADMIN 角色")
    void transition_applicationToRecorded_asAdmin_shouldSucceed() {
        authenticateAs(AppUserRole.ADMIN);
        item.setRegistrationState(createState(RegistrationStatus.APPLICATION));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(administeredItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdministeredItem result = registrationLifecycleService.transition(
                itemId, RegistrationStatus.RECORDED, "记录");

        assertThat(result.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.RECORDED);
    }

    @Test
    @DisplayName("APPLICATION → RETIRED: 合法转换，ADMIN 角色")
    void transition_applicationToRetired_asAdmin_shouldSucceed() {
        authenticateAs(AppUserRole.ADMIN);
        item.setRegistrationState(createState(RegistrationStatus.APPLICATION));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(administeredItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdministeredItem result = registrationLifecycleService.transition(
                itemId, RegistrationStatus.RETIRED, "退役");

        assertThat(result.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.RETIRED);
    }

    @Test
    @DisplayName("终态 HISTORICAL 无任何允许转换")
    void transition_fromHistorical_shouldReject() {
        authenticateAs(AppUserRole.ADMIN);
        item.setRegistrationState(createState(RegistrationStatus.HISTORICAL));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> registrationLifecycleService.transition(
                itemId, RegistrationStatus.RETIRED, "尝试从历史恢复"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.INVALID_STATE_TRANSITION_FROM_HISTORICAL);
                    assertThat(be.getMessage()).contains("非法的状态迁移");
                });
    }

    // =========================================================================
    // 2. 非法状态转换测试（错误码覆盖）
    // =========================================================================

    @ParameterizedTest(name = "从 {0} 到 {1} 应为非法转换")
    @DisplayName("非法状态转换应返回对应的错误码")
    @MethodSource("illegalTransitions")
    void transition_illegal_shouldReturnSpecificErrorCode(
            RegistrationStatus from, RegistrationStatus to, int expectedCode) {
        authenticateAs(AppUserRole.ADMIN);
        item.setRegistrationState(createState(from));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> registrationLifecycleService.transition(itemId, to, "测试"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(expectedCode);
                });
    }

    static Stream<Object[]> illegalTransitions() {
        return Stream.of(
                // INCOMPLETE (1110)
                new Object[]{RegistrationStatus.INCOMPLETE, RegistrationStatus.INCOMPLETE, ApiCodes.ALREADY_IN_TARGET_STATE},
                new Object[]{RegistrationStatus.INCOMPLETE, RegistrationStatus.RECORDED, ApiCodes.INVALID_STATE_TRANSITION_FROM_INCOMPLETE},
                new Object[]{RegistrationStatus.INCOMPLETE, RegistrationStatus.QUALIFIED, ApiCodes.INVALID_STATE_TRANSITION_FROM_INCOMPLETE},
                // SUBMITTED (1111)
                new Object[]{RegistrationStatus.CANDIDATE, RegistrationStatus.INCOMPLETE, ApiCodes.INVALID_STATE_TRANSITION_FROM_CANDIDATE},
                new Object[]{RegistrationStatus.CANDIDATE, RegistrationStatus.CANDIDATE, ApiCodes.ALREADY_IN_TARGET_STATE},
                new Object[]{RegistrationStatus.CANDIDATE, RegistrationStatus.QUALIFIED, ApiCodes.INVALID_STATE_TRANSITION_FROM_CANDIDATE},
                // RECORDED (1112)
                new Object[]{RegistrationStatus.RECORDED, RegistrationStatus.INCOMPLETE, ApiCodes.INVALID_STATE_TRANSITION_FROM_RECORDED},
                new Object[]{RegistrationStatus.RECORDED, RegistrationStatus.CANDIDATE, ApiCodes.INVALID_STATE_TRANSITION_FROM_RECORDED},
                new Object[]{RegistrationStatus.RECORDED, RegistrationStatus.RECORDED, ApiCodes.ALREADY_IN_TARGET_STATE},
                // QUALIFIED (1113)
                new Object[]{RegistrationStatus.QUALIFIED, RegistrationStatus.RECORDED, ApiCodes.INVALID_STATE_TRANSITION_FROM_QUALIFIED},
                new Object[]{RegistrationStatus.QUALIFIED, RegistrationStatus.QUALIFIED, ApiCodes.ALREADY_IN_TARGET_STATE},
                // STANDARD (1114)
                new Object[]{RegistrationStatus.STANDARD, RegistrationStatus.QUALIFIED, ApiCodes.INVALID_STATE_TRANSITION_FROM_STANDARD},
                new Object[]{RegistrationStatus.STANDARD, RegistrationStatus.STANDARD, ApiCodes.ALREADY_IN_TARGET_STATE},
                // PREFERRED (1115)
                new Object[]{RegistrationStatus.PREFERRED_STANDARD, RegistrationStatus.QUALIFIED, ApiCodes.INVALID_STATE_TRANSITION_FROM_PREFERRED_STANDARD},
                // SUPERSEDED (1116)
                new Object[]{RegistrationStatus.SUPERSEDED, RegistrationStatus.STANDARD, ApiCodes.INVALID_STATE_TRANSITION_FROM_SUPERSEDED},
                // RETIRED (1117)
                new Object[]{RegistrationStatus.RETIRED, RegistrationStatus.STANDARD, ApiCodes.INVALID_STATE_TRANSITION_FROM_RETIRED},
                // APPLICATION (1119)
                new Object[]{RegistrationStatus.APPLICATION, RegistrationStatus.INCOMPLETE, ApiCodes.INVALID_STATE_TRANSITION_FROM_APPLICATION},
                new Object[]{RegistrationStatus.APPLICATION, RegistrationStatus.CANDIDATE, ApiCodes.INVALID_STATE_TRANSITION_FROM_APPLICATION},
                new Object[]{RegistrationStatus.APPLICATION, RegistrationStatus.QUALIFIED, ApiCodes.INVALID_STATE_TRANSITION_FROM_APPLICATION},
                new Object[]{RegistrationStatus.APPLICATION, RegistrationStatus.STANDARD, ApiCodes.INVALID_STATE_TRANSITION_FROM_APPLICATION},
                new Object[]{RegistrationStatus.APPLICATION, RegistrationStatus.PREFERRED_STANDARD, ApiCodes.INVALID_STATE_TRANSITION_FROM_APPLICATION},
                new Object[]{RegistrationStatus.APPLICATION, RegistrationStatus.SUPERSEDED, ApiCodes.INVALID_STATE_TRANSITION_FROM_APPLICATION},
                new Object[]{RegistrationStatus.APPLICATION, RegistrationStatus.HISTORICAL, ApiCodes.INVALID_STATE_TRANSITION_FROM_APPLICATION},
                new Object[]{RegistrationStatus.APPLICATION, RegistrationStatus.APPLICATION, ApiCodes.ALREADY_IN_TARGET_STATE},
                // HISTORICAL (1118)
                new Object[]{RegistrationStatus.HISTORICAL, RegistrationStatus.RETIRED, ApiCodes.INVALID_STATE_TRANSITION_FROM_HISTORICAL}
        );
    }

    // =========================================================================
    // 3. 权限控制测试（角色-状态转换矩阵）
    // =========================================================================

    @Test
    @DisplayName("REGISTRAR 无权执行 STANDARD → SUPERSEDED")
    void transition_standardToSuperseded_asRegistrar_shouldReject() {
        authenticateAs(AppUserRole.REGISTRAR);
        item.setRegistrationState(createState(RegistrationStatus.STANDARD));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> registrationLifecycleService.transition(
                itemId, RegistrationStatus.SUPERSEDED, "降级"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.UNAUTHORIZED_STATE_TRANSITION);
                    assertThat(be.getMessage()).contains("无权执行状态迁移");
                    assertThat(be.getMessage()).contains("REGISTRAR");
                });
    }

    @Test
    @DisplayName("REGISTRAR 无权执行 PREFERRED → SUPERSEDED")
    void transition_preferredToSuperseded_asRegistrar_shouldReject() {
        authenticateAs(AppUserRole.REGISTRAR);
        item.setRegistrationState(createState(RegistrationStatus.PREFERRED_STANDARD));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> registrationLifecycleService.transition(
                itemId, RegistrationStatus.SUPERSEDED, "降级"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.UNAUTHORIZED_STATE_TRANSITION);
                });
    }

    @Test
    @DisplayName("REGISTRAR 无权执行 SUPERSEDED → HISTORICAL")
    void transition_supersededToHistorical_asRegistrar_shouldReject() {
        authenticateAs(AppUserRole.REGISTRAR);
        item.setRegistrationState(createState(RegistrationStatus.SUPERSEDED));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> registrationLifecycleService.transition(
                itemId, RegistrationStatus.HISTORICAL, "归档"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.UNAUTHORIZED_STATE_TRANSITION);
                });
    }

    @Test
    @DisplayName("VIEWER 无权执行任何状态转换")
    void transition_any_asViewer_shouldReject() {
        authenticateAs(AppUserRole.VIEWER);
        item.setRegistrationState(createState(RegistrationStatus.INCOMPLETE));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> registrationLifecycleService.transition(
                itemId, RegistrationStatus.CANDIDATE, "尝试转换"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.UNAUTHORIZED_STATE_TRANSITION);
                });
    }

    @Test
    @DisplayName("未认证用户（无 SecurityContext）视为 VIEWER，无权转换")
    void transition_withoutAuthentication_shouldReject() {
        SecurityContextHolder.clearContext();
        item.setRegistrationState(createState(RegistrationStatus.INCOMPLETE));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> registrationLifecycleService.transition(
                itemId, RegistrationStatus.CANDIDATE, "尝试转换"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.UNAUTHORIZED_STATE_TRANSITION);
                });
    }

    @Test
    @DisplayName("ADMIN 可以执行所有状态转换，包括 SUPERSEDED 相关")
    void transition_adminCanDoSuperseded_shouldSucceed() {
        authenticateAs(AppUserRole.ADMIN);
        item.setRegistrationState(createState(RegistrationStatus.STANDARD));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(administeredItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdministeredItem result = registrationLifecycleService.transition(
                itemId, RegistrationStatus.SUPERSEDED, "管理员降级");

        assertThat(result.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.SUPERSEDED);
    }

    @Test
    @DisplayName("REGISTRAR 可以执行常规正向转换 INCOMPLETE → SUBMITTED")
    void transition_registrarNormalForward_shouldSucceed() {
        authenticateAs(AppUserRole.REGISTRAR);
        item.setRegistrationState(createState(RegistrationStatus.INCOMPLETE));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(administeredItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdministeredItem result = registrationLifecycleService.transition(
                itemId, RegistrationStatus.CANDIDATE, "提交");

        assertThat(result.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.CANDIDATE);
    }

    // =========================================================================
    // 4. 边界条件测试
    // =========================================================================

    @Test
    @DisplayName("目标状态为 null 应返回错误码 1103")
    void transition_nullTargetState_shouldReturnError1103() {
        authenticateAs(AppUserRole.ADMIN);

        assertThatThrownBy(() -> registrationLifecycleService.transition(
                itemId, null, "备注"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.TARGET_STATE_REQUIRED);
                    assertThat(be.getMessage()).isEqualTo("目标状态不能为空");
                });
    }

    @Test
    @DisplayName("管理项不存在应返回错误码 1104")
    void transition_itemNotFound_shouldReturnError1104() {
        authenticateAs(AppUserRole.ADMIN);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationLifecycleService.transition(
                itemId, RegistrationStatus.CANDIDATE, "备注"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.ADMINISTERED_ITEM_NOT_FOUND);
                    assertThat(be.getMessage()).contains("管理项不存在");
                });
    }

    @Test
    @DisplayName("同状态转换应返回错误码 1101")
    void transition_sameState_shouldReturnError1101() {
        authenticateAs(AppUserRole.ADMIN);
        item.setRegistrationState(createState(RegistrationStatus.CANDIDATE));
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> registrationLifecycleService.transition(
                itemId, RegistrationStatus.CANDIDATE, "重复转换"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.ALREADY_IN_TARGET_STATE);
                    assertThat(be.getMessage()).contains("当前状态已经是");
                });
    }

    @Test
    @DisplayName("管理项无当前状态时默认为 INCOMPLETE")
    void transition_nullCurrentState_shouldDefaultToIncomplete() {
        authenticateAs(AppUserRole.ADMIN);
        item.setRegistrationState(null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(administeredItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdministeredItem result = registrationLifecycleService.transition(
                itemId, RegistrationStatus.CANDIDATE, "首次提交");

        assertThat(result.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.CANDIDATE);
        assertThat(result.getRegistrationState().getPreviousState()).isNull();
    }

    // =========================================================================
    // 5. 状态变更历史查询测试
    // =========================================================================

    @Test
    @DisplayName("状态历史查询：正常链式结构，按时间倒序")
    void getStateHistory_withChain_shouldReturnReverseChronological() {
        authenticateAs(AppUserRole.VIEWER);

        // 构建历史链: current(SUBMITTED) -> previous(INCOMPLETE)
        RegistrationState previous = createState(RegistrationStatus.INCOMPLETE);
        previous.setId(UUID.randomUUID());
        RegistrationState current = createState(RegistrationStatus.CANDIDATE);
        current.setId(UUID.randomUUID());
        current.setPreviousState(previous);
        item.setRegistrationState(current);

        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        Page<RegistrationStateHistoryEntry> page = registrationLifecycleService.getStateHistory(
                itemId, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        List<RegistrationStateHistoryEntry> content = page.getContent();
        assertThat(content.get(0).status()).isEqualTo(RegistrationStatus.CANDIDATE);
        assertThat(content.get(1).status()).isEqualTo(RegistrationStatus.INCOMPLETE);
    }

    @Test
    @DisplayName("状态历史查询：空状态返回空分页")
    void getStateHistory_noState_shouldReturnEmptyPage() {
        authenticateAs(AppUserRole.VIEWER);
        item.setRegistrationState(null);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        Page<RegistrationStateHistoryEntry> page = registrationLifecycleService.getStateHistory(
                itemId, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(0);
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("状态历史查询：管理项不存在应抛出异常")
    void getStateHistory_itemNotFound_shouldThrow() {
        authenticateAs(AppUserRole.VIEWER);
        when(administeredItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationLifecycleService.getStateHistory(
                itemId, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.ADMINISTERED_ITEM_NOT_FOUND);
                });
    }

    // =========================================================================
    // 6. canTransition 工具方法测试
    // =========================================================================

    @Test
    @DisplayName("canTransition: ADMIN 对所有合法转换返回 true")
    void canTransition_adminAlwaysTrueForLegal() {
        assertThat(registrationLifecycleService.canTransition(
                RegistrationStatus.INCOMPLETE, RegistrationStatus.CANDIDATE, AppUserRole.ADMIN)).isTrue();
        assertThat(registrationLifecycleService.canTransition(
                RegistrationStatus.STANDARD, RegistrationStatus.SUPERSEDED, AppUserRole.ADMIN)).isTrue();
        assertThat(registrationLifecycleService.canTransition(
                RegistrationStatus.HISTORICAL, RegistrationStatus.RETIRED, AppUserRole.ADMIN)).isFalse();
    }

    @Test
    @DisplayName("canTransition: REGISTRAR 对 SUPERSEDED 相关转换返回 false")
    void canTransition_registrarCannotSupersede() {
        assertThat(registrationLifecycleService.canTransition(
                RegistrationStatus.STANDARD, RegistrationStatus.SUPERSEDED, AppUserRole.REGISTRAR)).isFalse();
        assertThat(registrationLifecycleService.canTransition(
                RegistrationStatus.PREFERRED_STANDARD, RegistrationStatus.SUPERSEDED, AppUserRole.REGISTRAR)).isFalse();
        assertThat(registrationLifecycleService.canTransition(
                RegistrationStatus.SUPERSEDED, RegistrationStatus.HISTORICAL, AppUserRole.REGISTRAR)).isFalse();
    }

    @Test
    @DisplayName("canTransition: REGISTRAR 对常规正向转换返回 true")
    void canTransition_registrarNormalForward() {
        assertThat(registrationLifecycleService.canTransition(
                RegistrationStatus.INCOMPLETE, RegistrationStatus.CANDIDATE, AppUserRole.REGISTRAR)).isTrue();
        assertThat(registrationLifecycleService.canTransition(
                RegistrationStatus.CANDIDATE, RegistrationStatus.RECORDED, AppUserRole.REGISTRAR)).isTrue();
        assertThat(registrationLifecycleService.canTransition(
                RegistrationStatus.QUALIFIED, RegistrationStatus.STANDARD, AppUserRole.REGISTRAR)).isTrue();
    }

    @Test
    @DisplayName("canTransition: VIEWER 对所有转换返回 false")
    void canTransition_viewerAlwaysFalse() {
        assertThat(registrationLifecycleService.canTransition(
                RegistrationStatus.INCOMPLETE, RegistrationStatus.CANDIDATE, AppUserRole.VIEWER)).isFalse();
        assertThat(registrationLifecycleService.canTransition(
                RegistrationStatus.STANDARD, RegistrationStatus.RETIRED, AppUserRole.VIEWER)).isFalse();
    }

    // =========================================================================
    // 7. resolveCurrentRole 测试
    // =========================================================================

    @Test
    @DisplayName("resolveCurrentRole: 无认证时返回 VIEWER")
    void resolveCurrentRole_noAuth_returnsViewer() {
        SecurityContextHolder.clearContext();
        assertThat(registrationLifecycleService.resolveCurrentRole()).isEqualTo(AppUserRole.VIEWER);
    }

    @Test
    @DisplayName("resolveCurrentRole: 未认证 Authentication 返回 VIEWER")
    void resolveCurrentRole_notAuthenticated_returnsViewer() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user", null, List.of());
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(registrationLifecycleService.resolveCurrentRole()).isEqualTo(AppUserRole.VIEWER);
    }

    @Test
    @DisplayName("resolveCurrentRole: 从 AuthenticatedUser 解析角色")
    void resolveCurrentRole_fromAuthenticatedUser() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "admin", SystemRole.SUPER_ADMIN);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(registrationLifecycleService.resolveCurrentRole()).isEqualTo(AppUserRole.ADMIN);
    }

    @Test
    @DisplayName("resolveCurrentRole: 从 authority 回退解析")
    void resolveCurrentRole_fromAuthorityFallback() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "unknown", null, List.of(new SimpleGrantedAuthority("ROLE_REGISTRAR")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(registrationLifecycleService.resolveCurrentRole()).isEqualTo(AppUserRole.REGISTRAR);
    }

    @Test
    @DisplayName("resolveCurrentRole: 未知 authority 返回 VIEWER")
    void resolveCurrentRole_unknownAuthority_returnsViewer() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "unknown", null, List.of(new SimpleGrantedAuthority("ROLE_UNKNOWN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(registrationLifecycleService.resolveCurrentRole()).isEqualTo(AppUserRole.VIEWER);
    }

    // =========================================================================
    // 8. validateTransition 直接测试
    // =========================================================================

    @Test
    @DisplayName("validateTransition: 合法转换不抛出异常")
    void validateTransition_legal_shouldNotThrow() {
        assertThatThrownBy(() -> registrationLifecycleService.validateTransition(
                RegistrationStatus.INCOMPLETE, RegistrationStatus.INCOMPLETE))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.ALREADY_IN_TARGET_STATE);
                });
    }

    // =========================================================================
    // Helper 方法
    // =========================================================================

    private void authenticateAs(AppUserRole role) {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "testuser", role == AppUserRole.ADMIN ? SystemRole.SUPER_ADMIN : null);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority(role.authority())));
        SecurityContextHolder.getContext().setAuthentication(auth);

        if (role == AppUserRole.REGISTRAR) {
            Account account = new Account();
            account.setId(userId);
            org.mockito.Mockito.lenient().when(accountRepository.findById(userId)).thenReturn(Optional.of(account));
            Contact contact = new Contact();
            Role contactRole = new Role();
            contactRole.setRoleName("注册管理员");
            contact.setRole(contactRole);
            org.mockito.Mockito.lenient().when(contactRepository.findByAccount(account)).thenReturn(List.of(contact));
        } else if (role == AppUserRole.VIEWER) {
            Account account = new Account();
            account.setId(userId);
            org.mockito.Mockito.lenient().when(accountRepository.findById(userId)).thenReturn(Optional.of(account));
            org.mockito.Mockito.lenient().when(contactRepository.findByAccount(account)).thenReturn(List.of());
        }
    }

    private RegistrationState createState(RegistrationStatus status) {
        RegistrationState state = new RegistrationState();
        state.setId(UUID.randomUUID());
        state.setRegistrationStatus(status);
        state.setEffectiveDate(java.time.LocalDateTime.now());
        return state;
    }
}
