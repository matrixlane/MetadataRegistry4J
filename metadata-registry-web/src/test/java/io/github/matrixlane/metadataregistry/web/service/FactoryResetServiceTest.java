package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.system.FactoryResetRequest;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.seed.StaticDataPresetService;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseCleaner;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link FactoryResetService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class FactoryResetServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private StaticDataPresetService staticDataPresetService;

    @Mock
    private DatabaseCleaner databaseCleaner;

    @Mock
    private DatabaseEnvironment databaseEnvironment;

    @InjectMocks
    private FactoryResetService factoryResetService;

    private UUID userId;
    private Account adminUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        adminUser = new Account();
        adminUser.setId(userId);
        adminUser.setUsername("admin");
        adminUser.setPasswordHash("$2a$10$hashedPassword");
        adminUser.setSysRole(SystemRole.SUPER_ADMIN);
        adminUser.setEnabled(true);
        lenient().when(databaseEnvironment.qualify(anyString()))
                .thenAnswer(inv -> "mdr." + inv.getArgument(0));
        lenient().when(databaseEnvironment.qualifyExt(anyString()))
                .thenAnswer(inv -> "ext." + inv.getArgument(0));
        lenient().when(databaseEnvironment.toJdbcUuid(any(UUID.class)))
                .thenAnswer(inv -> inv.getArgument(0).toString());
    }

    @Test
    @DisplayName("恢复出厂设置：用户不存在应抛出 ResourceNotFoundException")
    void executeFactoryReset_userNotFound_shouldThrow() {
        when(accountRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> factoryResetService.executeFactoryReset(
                userId, new FactoryResetRequest("Admin@123", null), "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account");
    }

    @Test
    @DisplayName("恢复出厂设置：密码错误应抛出 BusinessException")
    void executeFactoryReset_wrongPassword_shouldThrow() {
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("wrongPassword", adminUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> factoryResetService.executeFactoryReset(
                userId, new FactoryResetRequest("wrongPassword", null), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.UNAUTHORIZED);
                    assertThat(be.getMessage()).contains("密码验证失败");
                });

        // 密码校验失败不得触达任何写库
        verify(databaseCleaner, never()).clearTables(any());
        verify(jdbcTemplate, never()).update(startsWith("INSERT INTO"), any(Object[].class));
    }

    @Test
    @DisplayName("恢复出厂设置：团队版显式版本分支与空版本行为一致（密码错误 401）")
    void executeFactoryReset_explicitTeamEdition_shouldUsePassword() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.TEAM);
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("wrongPassword", adminUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> factoryResetService.executeFactoryReset(
                userId, new FactoryResetRequest("wrongPassword", null), "192.168.1.10"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.UNAUTHORIZED));

        // 团队版不附加本机限制：非回环来源照常进入密码校验
        verify(databaseCleaner, never()).clearTables(any());
    }

    @Test
    @DisplayName("恢复出厂设置：团队版空密码按认证失败处理（400/401），不构成写库")
    void executeFactoryReset_teamBlankPassword_shouldThrow401() {
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("", adminUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> factoryResetService.executeFactoryReset(
                userId, new FactoryResetRequest("", null), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.UNAUTHORIZED));

        verify(databaseCleaner, never()).clearTables(any());
    }

    @Test
    @DisplayName("恢复出厂设置：正常流程应委托 DatabaseCleaner 清空、重建管理员、重置初始化状态")
    void executeFactoryReset_success_shouldClearTablesAndReseed() {
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("Admin@123", adminUser.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("Admin@123")).thenReturn("$2a$10$newHashedPassword");

        factoryResetService.executeFactoryReset(userId, new FactoryResetRequest("Admin@123", null), "127.0.0.1");

        // 清空委托给方言可移植的 DatabaseCleaner（保留 flyway 历史表）
        verify(databaseCleaner).clearTables(Set.of("flyway_schema_history"));

        // 重建管理员：方言限定表名 + 可移植时间函数 + 完整布尔列
        verify(jdbcTemplate).update(
                eq("INSERT INTO ext.account (id, username, password_hash, sys_role, enabled, created_at, force_password_reset, temp_password_used) " +
                   "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)"),
                any(), eq("admin"), eq("$2a$10$newHashedPassword"),
                eq("SUPER_ADMIN"), eq(true), eq(false), eq(false)
        );

        // 静态预置统一入口（语言代码与中文名称、全局默认策略、
        // 标准流转规则）——出厂清空后由 presetAll 幂等恢复
        verify(staticDataPresetService).presetAll();
    }

    @Test
    @DisplayName("恢复出厂设置：DatabaseCleaner 清空失败应中断并传播异常")
    @MockitoSettings(strictness = Strictness.LENIENT)
    void executeFactoryReset_clearFails_shouldPropagate() {
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("Admin@123", adminUser.getPasswordHash())).thenReturn(true);
        doThrow(new IllegalStateException("清空业务表失败: mock"))
                .when(databaseCleaner).clearTables(Set.of("flyway_schema_history"));

        assertThatThrownBy(() -> factoryResetService.executeFactoryReset(
                userId, new FactoryResetRequest("Admin@123", null), "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("清空业务表失败");

        // 清空失败时不得重建管理员
        verify(jdbcTemplate, never()).update(startsWith("INSERT INTO"), any(Object[].class));
    }

    @Test
    @DisplayName("恢复出厂设置：清空完成后总是重建管理员和重置状态")
    void executeFactoryReset_noTables_shouldStillReseedAndReset() {
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("Admin@123", adminUser.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("Admin@123")).thenReturn("$2a$10$encoded");

        factoryResetService.executeFactoryReset(userId, new FactoryResetRequest("Admin@123", null), "127.0.0.1");

        verify(databaseCleaner).clearTables(Set.of("flyway_schema_history"));

        verify(jdbcTemplate).update(startsWith("INSERT INTO ext.account"), any(Object[].class));
    }

    // =========================================================================
    // 个人版（PERSONAL）：确认短语 + 本机来源
    // =========================================================================

    @Test
    @DisplayName("个人版：确认短语正确且本机来源应执行，重建管理员为 NO_PASSWORD 哨兵")
    void personal_correctPhraseLoopback_shouldResetAndReseedNoPassword() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        factoryResetService.executeFactoryReset(
                userId, new FactoryResetRequest(null, "恢复出厂设置"), "127.0.0.1");

        verify(databaseCleaner).clearTables(Set.of("flyway_schema_history"));
        // 个人版无密码概念：重建 admin 写入 NO_PASSWORD 明文哨兵，且不做任何密码比对
        verify(jdbcTemplate).update(
                startsWith("INSERT INTO ext.account"), any(Object[].class));
        verify(jdbcTemplate).update(
                eq("INSERT INTO ext.account (id, username, password_hash, sys_role, enabled, created_at, force_password_reset, temp_password_used) " +
                   "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)"),
                any(), eq("admin"), eq("NO_PASSWORD"), eq("SUPER_ADMIN"), eq(true), eq(false), eq(false));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("个人版：IPv6 回环 ::1 视为本机来源")
    void personal_ipv6Loopback_shouldProceed() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        factoryResetService.executeFactoryReset(
                userId, new FactoryResetRequest(null, "恢复出厂设置"), "::1");

        verify(databaseCleaner).clearTables(Set.of("flyway_schema_history"));
    }

    @Test
    @DisplayName("个人版：确认短语错误应抛 422 且不触达清表")
    void personal_wrongPhrase_shouldThrow422() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> factoryResetService.executeFactoryReset(
                userId, new FactoryResetRequest(null, "恢复出厂"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED);
                    assertThat(be.getMessage()).contains("确认短语不正确");
                });

        verify(databaseCleaner, never()).clearTables(any());
    }

    @Test
    @DisplayName("个人版：确认短语为空应抛 422")
    void personal_blankPhrase_shouldThrow422() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> factoryResetService.executeFactoryReset(
                userId, new FactoryResetRequest(null, "  "), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.VALIDATION_FAILED));

        verify(databaseCleaner, never()).clearTables(any());
    }

    @Test
    @DisplayName("个人版：非本机来源应抛 403，即便短语正确也不触达清表")
    void personal_nonLoopback_shouldThrow403() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> factoryResetService.executeFactoryReset(
                userId, new FactoryResetRequest(null, "恢复出厂设置"), "192.168.1.10"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.FORBIDDEN);
                    assertThat(be.getMessage()).contains("仅允许从本机发起");
                });

        verify(databaseCleaner, never()).clearTables(any());
        verify(jdbcTemplate, never()).update(startsWith("INSERT INTO"), any(Object[].class));
    }

    @Test
    @DisplayName("个人版：密码不构成确认凭据——密码正确但短语错误仍拒绝")
    void personal_correctPasswordWrongPhrase_shouldThrow422() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> factoryResetService.executeFactoryReset(
                userId, new FactoryResetRequest("Admin@123", "恢复出厂"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ApiCodes.VALIDATION_FAILED));

        verify(databaseCleaner, never()).clearTables(any());
    }

    @Test
    @DisplayName("个人版：来源地址仅接受 IP 字面量——null/畸形/主机名形态一律拒绝且不做 DNS")
    void personal_nonLiteralRemoteAddr_shouldThrow403() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));

        // null、畸形 IPv4、越界八位组、主机名、可解析到回环的主机名、非回环 IPv6
        String[] rejected = { null, "999.999.999.999", "256.0.0.1", "localhost",
                "127.0.0.1.nip.io", "bad.dotted.name", "fe80::1", "10.0.0.1", "" };
        for (String addr : rejected) {
            assertThatThrownBy(() -> factoryResetService.executeFactoryReset(
                    userId, new FactoryResetRequest(null, "恢复出厂设置"), addr))
                    .as("remoteAddr=%s", addr)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo(ApiCodes.FORBIDDEN));
        }

        // 各形态回环字面量均放行
        String[] accepted = { "127.0.0.1", "127.255.255.255", "0:0:0:0:0:0:0:1", "::ffff:127.0.0.1" };
        for (String addr : accepted) {
            assertThatCode(() -> factoryResetService.executeFactoryReset(
                    userId, new FactoryResetRequest(null, "恢复出厂设置"), addr))
                    .as("remoteAddr=%s", addr)
                    .doesNotThrowAnyException();
        }

        // 拒绝与放行合计各执行一次清表（仅 accepted 组）
        verify(databaseCleaner, times(accepted.length)).clearTables(any());
    }

    private static String startsWith(String prefix) {
        return argThat(s -> s != null && s.startsWith(prefix));
    }
}
