package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.web.auth.dto.AccountCreateRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.ChangePasswordRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.LoginRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.LoginResponse;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.individual.IndividualRequest;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.IndividualRepository;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AccountRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private IndividualRepository individualRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository registrarRepository;

    @Mock
    private io.github.matrixlane.metadataregistry.web.service.ContactAccountBindingService contactAccountBindingService;

    @Mock
    private SystemSettingService systemSettingService;

    @InjectMocks
    private AuthService authService;

    private static final String EMERGENCY_CODE_FILE = "emergency-code.txt";

    @BeforeEach
    void setUp() {
        // Delete emergency-code.txt if it exists to ensure a clean state
        new File(EMERGENCY_CODE_FILE).delete();
    }

    @AfterEach
    void tearDown() {
        // Clean up emergency-code.txt
        new File(EMERGENCY_CODE_FILE).delete();
    }

    @Test
    @DisplayName("首次登录：超级管理员(admin)密码哈希为 NO_PASSWORD 时免密登录，且强制改密")
    void login_adminFirstLoginPasswordless_shouldSucceedWithForceReset() {
        Account admin = new Account();
        admin.setUsername("admin");
        admin.setPasswordHash("NO_PASSWORD");
        admin.setEnabled(true);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        lenient().when(contactAccountBindingService.contactsOfAccount(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        when(jwtService.generateToken(eq(admin), eq(true), eq(false), eq(false), eq(false), eq(false))).thenReturn("mock-token");

        LoginRequest request = new LoginRequest("admin", "");
        LoginResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("mock-token");
        assertThat(admin.isForcePasswordReset()).isTrue();
        verify(userRepository).save(admin);
    }

    @Test
    @DisplayName("应急登录：超级管理员使用本地 emergency-code.txt 中的应急码登录成功，重新生成应急码且强制改密")
    void login_adminEmergencyLogin_shouldSucceedAndRegenerateEmergencyCode() throws IOException {
        Files.writeString(Paths.get(EMERGENCY_CODE_FILE), "emergency123");

        Account admin = new Account();
        admin.setUsername("admin");
        admin.setPasswordHash("$2a$10$hashedAdminPassword");
        admin.setEnabled(true);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        lenient().when(contactAccountBindingService.contactsOfAccount(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        when(jwtService.generateToken(eq(admin), eq(true), eq(true), eq(false), eq(false), eq(false))).thenReturn("mock-token");

        LoginRequest request = new LoginRequest("admin", "emergency123");
        LoginResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("mock-token");
        assertThat(admin.isForcePasswordReset()).isTrue();
        verify(userRepository).save(admin);

        // Verify emergency code is regenerated and changed
        String newCode = Files.readString(Paths.get(EMERGENCY_CODE_FILE)).trim();
        assertThat(newCode).isNotEqualTo("emergency123");
        assertThat(newCode).hasSize(10);
    }

    @Test
    @DisplayName("临时密码登录：首次登录成功后将密码哈希标记为 USED:，标记强制改密")
    void login_temporaryPasswordFirstUse_shouldSucceedAndMarkAsUsed() {
        Account user = new Account();
        user.setUsername("testuser");
        user.setPasswordHash("TEMP:$2a$10$hashedTempPassword");
        user.setEnabled(true);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("temp123", "$2a$10$hashedTempPassword")).thenReturn(true);
        lenient().when(contactAccountBindingService.contactsOfAccount(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        when(jwtService.generateToken(eq(user), eq(true), eq(false), eq(true), eq(false), eq(false))).thenReturn("mock-token");

        LoginRequest request = new LoginRequest("testuser", "temp123");
        LoginResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("mock-token");
        assertThat(user.getPasswordHash()).startsWith("USED:$2a$10$hashedTempPassword");
        assertThat(user.isForcePasswordReset()).isTrue();
        assertThat(user.isTempPasswordUsed()).isTrue();
        verify(userRepository, times(2)).save(user); // once for updating hash/reset state, once for updating lastLoginAt
    }

    @Test
    @DisplayName("临时密码登录：已被使用过的临时密码（USED:）登录应抛出 BadCredentialsException 异常")
    void login_temporaryPasswordUsed_shouldThrowBadCredentials() {
        Account user = new Account();
        user.setUsername("testuser");
        user.setPasswordHash("USED:$2a$10$hashedTempPassword");
        user.setEnabled(true);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("testuser", "temp123");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("临时密码已失效");
    }

    @Test
    @DisplayName("修改密码：新密码与原密码相同时应抛出 BusinessException 异常")
    void changePassword_newPasswordSameAsOld_shouldThrow() {
        UUID userId = UUID.randomUUID();
        Account user = new Account();
        user.setId(userId);
        user.setPasswordHash("$2a$10$hashedPassword");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ChangePasswordRequest request = new ChangePasswordRequest("password123", "password123");

        assertThatThrownBy(() -> authService.changePassword(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新密码不能与原密码相同");
    }

    @Test
    @DisplayName("重置密码：重置普通用户密码应生成随机临时密码，以 TEMP: 前缀保存哈希，并清除旧的已使用标记")
    void resetPassword_normalUser_shouldGenerateTempPasswordAndResetState() {
        UUID userId = UUID.randomUUID();
        Account user = new Account();
        user.setId(userId);
        user.setUsername("normalUser");
        user.setSysRole(null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("mockEncodedTempPassword");

        String tempPassword = authService.resetPassword(userId);

        assertThat(tempPassword).hasSize(10);
        assertThat(user.getPasswordHash()).isEqualTo("TEMP:mockEncodedTempPassword");
        assertThat(user.isForcePasswordReset()).isTrue();
        assertThat(user.isTempPasswordUsed()).isTrue(); // wait, does resetPassword set it to true? Yes, user.setTempPasswordUsed(true) is in the code.
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("重置密码：不允许重置超级管理员账号密码，应抛出 BusinessException 异常")
    void resetPassword_superAdmin_shouldThrow() {
        UUID userId = UUID.randomUUID();
        Account user = new Account();
        user.setId(userId);
        user.setUsername("admin");
        user.setSysRole(SystemRole.SUPER_ADMIN);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resetPassword(userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重置超级管理员的密码");
    }

    @Test
    @DisplayName("用户自助注册：当系统关闭注册功能（allow_user_registration = false）时，注册应抛出 BusinessException 异常")
    void register_registrationDisabled_shouldThrow() {
        when(systemSettingService.isAllowUserRegistration()).thenReturn(false);

        AccountCreateRequest request = new AccountCreateRequest("newuser", "password123", null, true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统已关闭自主注册功能");
    }

    @Test
    @DisplayName("保存我的姓名：账号绑定 Registrar 已有 Individual 时复用该 Individual（就地更新），不新建")
    void updateMyIndividual_firstTime_reusesRegistrarIndividual() {
        UUID userId = UUID.randomUUID();
        Account user = new Account();
        user.setId(userId);
        user.setUsername("admin");

        Individual existing = new Individual();
        existing.setIdentifier("admin");
        Registrar registrar = new Registrar();
        registrar.setIndividual(existing);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(individualRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contactAccountBindingService.contactsOfAccount(user)).thenReturn(java.util.List.of(registrar));
        when(contactAccountBindingService.registrarsOfAccount(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        when(contactAccountBindingService.registrarsOfAccount(userId)).thenReturn(java.util.List.of(registrar));

        IndividualRequest request = new IndividualRequest("汪汇", null, null, null, null, null);
        authService.updateMyIndividual(userId, request);

        // 复用同一行：Account 与 Registrar 指向同一 Individual，姓名就地更新
        assertThat(user.getIndividual()).isSameAs(existing);
        assertThat(existing.getName().getValue()).isEqualTo("汪汇");
        verify(userRepository).save(user);
        verify(registrarRepository, never()).save(any());
    }

    @Test
    @DisplayName("保存我的姓名：首次保存且无 Individual 可复用时新建，并回填到账号绑定的 Registrar")
    void updateMyIndividual_firstTime_backfillsBoundRegistrar() {
        UUID userId = UUID.randomUUID();
        Account user = new Account();
        user.setId(userId);
        user.setUsername("zhangsan");

        Registrar registrar = new Registrar();
        registrar.setIndividual(null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(individualRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contactAccountBindingService.contactsOfAccount(user)).thenReturn(java.util.List.of(registrar));
        when(contactAccountBindingService.registrarsOfAccount(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        when(contactAccountBindingService.registrarsOfAccount(userId)).thenReturn(java.util.List.of(registrar));

        IndividualRequest request = new IndividualRequest("张三", null, null, null, null, null);
        authService.updateMyIndividual(userId, request);

        assertThat(user.getIndividual()).isNotNull();
        assertThat(registrar.getIndividual()).isNotNull();
        assertThat(registrar.getIndividual().getName().getValue()).isEqualTo("张三");
        verify(contactRepository).save(registrar);
    }

    @Test
    @DisplayName("登录：已登记联系人的普通账号下发 submitter=true，profile 中 hasContact=true、profilePrompt=false")
    void login_submitterAccount_flagged() {
        Account user = new Account();
        user.setId(UUID.randomUUID());
        user.setUsername("submitter1");
        user.setPasswordHash("$2a$10$hashedPassword");
        user.setEnabled(true);

        when(userRepository.findByUsername("submitter1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "$2a$10$hashedPassword")).thenReturn(true);
        when(contactAccountBindingService.registrarsOfAccount(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        when(contactAccountBindingService.hasAnyContact(user.getId())).thenReturn(true);
        when(jwtService.generateToken(eq(user), eq(false), eq(false), eq(false), eq(false), eq(true)))
                .thenReturn("mock-token");

        LoginResponse response = authService.login(new LoginRequest("submitter1", "pass123"));

        assertThat(response.token()).isEqualTo("mock-token");
        assertThat(response.user().hasContact()).isTrue();
        assertThat(response.user().profilePrompt()).isFalse();
        assertThat(response.user().isRegistrar()).isFalse();
    }

    @Test
    @DisplayName("登录：既非 Registrar 也非 Contact 的普通账号 profilePrompt=true")
    void login_plainAccount_profilePrompted() {
        Account user = new Account();
        user.setId(UUID.randomUUID());
        user.setUsername("plain1");
        user.setPasswordHash("$2a$10$hashedPassword");
        user.setEnabled(true);

        when(userRepository.findByUsername("plain1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "$2a$10$hashedPassword")).thenReturn(true);
        when(contactAccountBindingService.registrarsOfAccount(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        when(contactAccountBindingService.hasAnyContact(user.getId())).thenReturn(false);
        when(jwtService.generateToken(eq(user), eq(false), eq(false), eq(false), eq(false), eq(false)))
                .thenReturn("mock-token");

        LoginResponse response = authService.login(new LoginRequest("plain1", "pass123"));

        assertThat(response.user().hasContact()).isFalse();
        assertThat(response.user().profilePrompt()).isTrue();
    }

    @Test
    @DisplayName("登录：SUPER_ADMIN 恒为 Registrar，profilePrompt=false")
    void login_superAdmin_noProfilePrompt() {
        Account admin = new Account();
        admin.setId(UUID.randomUUID());
        admin.setUsername("admin");
        admin.setPasswordHash("$2a$10$hashedAdminPassword");
        admin.setEnabled(true);
        admin.setSysRole(SystemRole.SUPER_ADMIN);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("admin123", "$2a$10$hashedAdminPassword")).thenReturn(true);
        when(jwtService.generateToken(eq(admin), eq(false), eq(false), eq(false), eq(true), eq(false)))
                .thenReturn("mock-token");

        LoginResponse response = authService.login(new LoginRequest("admin", "admin123"));

        assertThat(response.user().isRegistrar()).isTrue();
        assertThat(response.user().profilePrompt()).isFalse();
    }
}
