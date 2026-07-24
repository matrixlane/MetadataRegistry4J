package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSetupRequest;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.seed.SystemDefaultNamespaceInitializer;
import java.util.Optional;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class InitWizardServiceIntegrationTest {

    @Autowired
    private InitWizardService initWizardService;

    @Autowired
    private SystemSettingService systemSettingService;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RegistrarRepository registrarRepository;

    @Autowired
    private RegistrationAuthorityRepository registrationAuthorityRepository;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private LanguageIdentificationRepository languageRepository;

    @Autowired
    private Optional<SystemDefaultNamespaceInitializer> systemDefaultNamespaceInitializer;

    private UUID appUserId;

    @BeforeEach
    void setUp() {
        // Ensure system setting initialized is false before test
        systemSettingService.setInitialized(false);
        systemSettingService.setRunningMode(null);

        // Create a test Super Admin user
        Account adminUser = new Account();
        adminUser.setUsername("test-superadmin-" + UUID.randomUUID().toString().substring(0, 8));
        adminUser.setPasswordHash("password");
        adminUser.setSysRole(SystemRole.SUPER_ADMIN);
        Account savedUser = accountRepository.save(adminUser);
        appUserId = savedUser.getId();
    }

    @Test
    void setup_shouldCreateAllEntitiesAndMarkInitialized() {
        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "TEAM",
                null,
                "Integration Test RA",
                "Integration Test RA English",
                "ra@integration.com",
                true, // selfAsRegistrar
                true,  // createNamespace
                false, null, null, null, null, null, null, null, null
        );

        initWizardService.setup(request, appUserId);

        // 1. setup() must NOT flip system.initialized — that happens in commit()
        assertThat(systemSettingService.isInitialized()).isFalse();
        assertThat(systemSettingService.getRunningMode()).isEqualTo("SINGLE");

        // 2. Verify RegistrationAuthority is created
        List<RegistrationAuthority> authorities = registrationAuthorityRepository.findAll();
        RegistrationAuthority createdRa = authorities.stream()
                .filter(ra -> !ra.getName().isEmpty() && "Integration Test RA".equals(ra.getName().get(0).getValue()))
                .findFirst()
                .orElse(null);
        assertThat(createdRa).isNotNull();
        assertThat(createdRa.getRegistrationAuthorityIdentifier()).isNotNull();
        assertThat(createdRa.getRegistrationAuthorityIdentifier().getOrganizationIdentifier()).isEqualTo("RA");
        assertThat(createdRa.getName().size()).isEqualTo(2);
        assertThat(createdRa.getName().get(1).getValue()).isEqualTo("Integration Test RA English");
        assertThat(createdRa.getEmailAddress()).isNotEmpty();
        assertThat(createdRa.getEmailAddress().get(0).getValue()).isEqualTo("ra@integration.com");

        // 3. Verify Default Namespace is created
        List<Namespace> namespaces = namespaceRepository.findAll();
        Namespace createdNs = namespaces.stream()
                .filter(ns -> ns.getRegistrationAuthority() != null && createdRa.getId().equals(ns.getRegistrationAuthority().getId()))
                .findFirst()
                .orElse(null);
        assertThat(createdNs).isNotNull();
        assertThat(createdNs.getDesignations()).isNotEmpty();
        assertThat(createdNs.getDesignations().get(0).getSign().getValue()).isEqualTo("默认命名空间");

        // 4. Verify Super Admin User is assigned Registrar contact
        List<Registrar> registrars = registrarRepository.findAll();
        boolean hasRegistrarRole = registrars.stream()
                .anyMatch(r -> r.getAccount() != null && appUserId.equals(r.getAccount().getId()) &&
                        r.getRole() != null && "注册管理员".equals(r.getRole().getRoleName()) &&
                        r.getRegistrationAuthority() != null && createdRa.getId().equals(r.getRegistrationAuthority().getId()));
        assertThat(hasRegistrarRole).isTrue();

        // 5. Verify G20 languages are initialized
        assertThat(languageRepository.count()).isGreaterThanOrEqualTo(22);
        assertThat(languageRepository.findByLanguageIdentifier("zh-CN")).isPresent();
        assertThat(languageRepository.findByLanguageIdentifier("en-US")).isPresent();

        // 6. Verify System Default Namespace is created (if initializer bean is present)
        if (systemDefaultNamespaceInitializer.isPresent()) {
            assertThat(namespaceRepository.existsByShorthandPrefix("sys:")).isTrue();
        }
    }

    @Test
    void setup_shouldResetSuperAdminToNoPassword() {
        // Simulate the post-factory-reset state: system is uninitialized but
        // the SUPER_ADMIN still holds a real password hash. Without the reset,
        // commit() would reject every attempt ("not in NO_PASSWORD state")
        // and the wizard could never complete.
        Account admin = accountRepository.findById(appUserId).orElseThrow();
        admin.setPasswordHash("$2a$10$realhashfromprevioussession");
        admin.setForcePasswordReset(true);
        accountRepository.save(admin);

        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "PERSONAL",
                null,
                "Integration Test RA",
                null,
                null,
                false, // selfAsRegistrar
                false, // createNamespace
                false, null, null, null, null, null, null, null, null
        );

        initWizardService.setup(request, appUserId);

        Account reloaded = accountRepository.findById(appUserId).orElseThrow();
        assertThat(reloaded.getPasswordHash()).isEqualTo("NO_PASSWORD");
        assertThat(reloaded.isForcePasswordReset()).isFalse();
        assertThat(reloaded.isTempPasswordUsed()).isFalse();
        assertThat(reloaded.getLastLoginAt()).isNull();
    }

    @Test
    void setup_personalEdition_appliesDefaults() {
        // PERSONAL 版：治理字段全部留空，系统应以默认值自动创建注册机构、
        // 命名空间与注册表规范，并把管理员设为注册员。
        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "PERSONAL",
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null
        );

        initWizardService.setup(request, appUserId);

        List<RegistrationAuthority> authorities = registrationAuthorityRepository.findAll();
        RegistrationAuthority ra = authorities.stream()
                .filter(a -> !a.getName().isEmpty() && "个人注册机构".equals(a.getName().get(0).getValue()))
                .findFirst()
                .orElse(null);
        assertThat(ra).isNotNull();

        // 管理员自动成为注册员
        List<Registrar> registrars = registrarRepository.findAll();
        boolean hasRegistrar = registrars.stream()
                .anyMatch(r -> r.getAccount() != null && appUserId.equals(r.getAccount().getId()));
        assertThat(hasRegistrar).isTrue();

        // 默认命名空间已创建
        assertThat(namespaceRepository.count()).isGreaterThanOrEqualTo(1);

        // 版本草稿已写入，系统仍未初始化（等 commit）
        assertThat(systemSettingService.getEdition().name()).isEqualTo("PERSONAL");
        assertThat(systemSettingService.isInitialized()).isFalse();
    }

    @Test
    void setup_cloudEdition_rejected() {
        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "CLOUD",
                null, "Some RA", null, null,
                true, true, false, null, null,
                null, null, null, null, null, null
        );

        assertThatThrownBy(() -> initWizardService.setup(request, appUserId))
                .hasMessageContaining("不可用");
    }
}
