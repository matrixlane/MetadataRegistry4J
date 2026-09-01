package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseCleaner;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.github.matrixlane.metadataregistry.web.seed.LanguageInitializer;
import io.github.matrixlane.metadataregistry.web.seed.SystemDefaultNamespaceInitializer;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    private LanguageInitializer languageInitializer;

    @Mock
    private ObjectProvider<SystemDefaultNamespaceInitializer> systemDefaultNamespaceInitializerProvider;

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

        assertThatThrownBy(() -> factoryResetService.executeFactoryReset(userId, "Admin@123"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account");
    }

    @Test
    @DisplayName("恢复出厂设置：密码错误应抛出 BusinessException")
    void executeFactoryReset_wrongPassword_shouldThrow() {
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("wrongPassword", adminUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> factoryResetService.executeFactoryReset(userId, "wrongPassword"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.UNAUTHORIZED);
                    assertThat(be.getMessage()).contains("密码验证失败");
                });

        verify(jdbcTemplate, never()).queryForList(anyString(), eq(String.class));
    }

    @Test
    @DisplayName("恢复出厂设置：正常流程应委托 DatabaseCleaner 清空、重建管理员、重置初始化状态")
    void executeFactoryReset_success_shouldClearTablesAndReseed() {
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("Admin@123", adminUser.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("Admin@123")).thenReturn("$2a$10$newHashedPassword");

        factoryResetService.executeFactoryReset(userId, "Admin@123");

        // 清空委托给方言可移植的 DatabaseCleaner（保留 flyway 历史表）
        verify(databaseCleaner).clearTables(java.util.Set.of("flyway_schema_history"));

        // 重建管理员：方言限定表名 + 可移植时间函数 + 完整布尔列
        verify(jdbcTemplate).update(
                eq("INSERT INTO ext.account (id, username, password_hash, sys_role, enabled, created_at, force_password_reset, temp_password_used) " +
                   "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)"),
                any(), eq("admin"), eq("$2a$10$newHashedPassword"),
                eq("SUPER_ADMIN"), eq(true), eq(false), eq(false)
        );

        verify(languageInitializer).run(null);
        verify(systemDefaultNamespaceInitializerProvider).ifAvailable(any());
    }

    @Test
    @DisplayName("恢复出厂设置：DatabaseCleaner 清空失败应中断并传播异常")
    @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void executeFactoryReset_clearFails_shouldPropagate() {
        when(accountRepository.findById(userId)).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("Admin@123", adminUser.getPasswordHash())).thenReturn(true);
        doThrow(new IllegalStateException("清空业务表失败: mock"))
                .when(databaseCleaner).clearTables(java.util.Set.of("flyway_schema_history"));

        assertThatThrownBy(() -> factoryResetService.executeFactoryReset(userId, "Admin@123"))
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

        factoryResetService.executeFactoryReset(userId, "Admin@123");

        verify(databaseCleaner).clearTables(java.util.Set.of("flyway_schema_history"));

        verify(jdbcTemplate).update(startsWith("INSERT INTO ext.account"), any(Object[].class));
    }

    private static String startsWith(String prefix) {
        return argThat(s -> s != null && s.startsWith(prefix));
    }
}
