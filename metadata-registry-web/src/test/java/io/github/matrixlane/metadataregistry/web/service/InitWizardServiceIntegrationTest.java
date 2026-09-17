package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfig;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSetupRequest;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSpecAttributes;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.DegreeOfConformance;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.seed.SystemDefaultNamespaceInitializer;
import java.util.Optional;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierStrategyConfigRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        // 取消用户主目录 application-local.yml 导入并显式锁定内存 SQLite,
        // 使本测试与宿主机数据源配置无关(导入的配置优先级高于非 profile 默认值)。
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:init-wizard-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main",
        // 置空 mapping-resources：用户主目录 application-local.yml 可能注入
        // orm-team.xml（SQL Server 专用），内存 SQLite 测试无需 EXT schema 覆盖。
        "spring.jpa.mapping-resources="
})
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
    private io.github.matrixlane.metadataregistry.web.repository.NamespaceIdentifierSettingsRepository namespaceIdentifierSettingsRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RegistrarRepository registrarRepository;
    @Autowired
    private ContactAccountBindingService contactAccountBindingService;

    @Autowired
    private RegistrySpecificationService registrySpecificationService;

    @Autowired
    private RegistrationAuthorityRepository registrationAuthorityRepository;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private IdentifierStrategyConfigRepository identifierStrategyConfigRepository;

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
                null,        // namespaceName（缺省 "默认命名空间"）
                null,        // identifierStrategy（缺省段模板）
                null,        // identifierTemplate（缺省系统默认模板）
                null,        // identifierInputMode
                null,        // versionInputMode
                "SEQUENTIAL",// versionPolicy
                true,        // createRegistrySpecification
                null, null, null
        ,
            null
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
        assertThat(createdRa.getEmailAddress().get(0)).isEqualTo("ra@integration.com");

        // 3. Verify Default Namespace is created
        List<Namespace> namespaces = namespaceRepository.findAll();
        Namespace createdNs = namespaces.stream()
                .filter(ns -> ns.getRegistrationAuthority() != null && createdRa.getId().equals(ns.getRegistrationAuthority().getId()))
                .findFirst()
                .orElse(null);
        assertThat(createdNs).isNotNull();
        assertThat(createdNs.getDesignations()).isNotEmpty();
        assertThat(createdNs.getDesignations().get(0).getSign().getValue()).isEqualTo("默认命名空间");
        // 版本生成策略归属于命名空间(扩展属性存于卫星表 namespace_identifier_settings)
        var nsSettings = namespaceIdentifierSettingsRepository.findByNamespaceId(createdNs.getId()).orElseThrow();
        assertThat(nsSettings.getVersioningPolicy()).isEqualTo("SEQUENTIAL");

        // 3.1 命名空间级标识生成策略配置已发布并绑定到该命名空间
        List<IdentifierStrategyConfig> nsConfigs = identifierStrategyConfigRepository.findAll().stream()
                .filter(c -> c.getNamespace() != null && createdNs.getId().equals(c.getNamespace().getId()))
                .toList();
        assertThat(nsConfigs).hasSize(1);
        IdentifierStrategyConfig nsConfig = nsConfigs.get(0);
        assertThat(nsConfig.getStrategyType()).isEqualTo("SEGMENT_TEMPLATE");
        assertThat(nsConfig.getStatus().name()).isEqualTo("PUBLISHED");
        // 系统默认模板：对象缩写 + 分隔符 + 顺序号（如 DE.00001）
        assertThat(nsConfig.getConfigJson()).contains("OBJECT_ABBREVIATION");
        // 标识/版本输入模式缺省为 AUTO_WITH_OVERRIDE
        assertThat(nsSettings.getIdentifierInputMode()).isEqualTo("AUTO_WITH_OVERRIDE");
        assertThat(nsSettings.getVersionInputMode()).isEqualTo("AUTO_WITH_OVERRIDE");

        // 4. Verify Super Admin User is assigned Registrar contact
        List<Registrar> registrars = registrarRepository.findAll();
        boolean hasRegistrarRole = registrars.stream()
                .anyMatch(r -> contactAccountBindingService.accountIdOfContact(r.getId()).map(appUserId::equals).orElse(false) &&
                        r.getRole() != null && "注册管理员".equals(r.getRole().getRoleName()) &&
                        r.getRegistrationAuthority() != null && createdRa.getId().equals(r.getRegistrationAuthority().getId()));
        assertThat(hasRegistrarRole).isTrue();

        // 5. Verify G20 languages are initialized
        assertThat(languageRepository.count()).isGreaterThanOrEqualTo(22);
        assertThat(languageRepository.findByLanguageIdentifier("zh-CN")).isPresent();
        assertThat(languageRepository.findByLanguageIdentifier("en-US")).isPresent();
        UUID zhCnLanguageId = languageRepository.findByLanguageIdentifier("zh-CN").orElseThrow().getId();

        // 5.1 Verify Registry Specification is created with default language
        assertThat(registrySpecificationService.exists()).isTrue();
        var specPage = registrySpecificationService.list(null, Pageable.ofSize(1));
        assertThat(specPage.getContent()).isNotEmpty();
        assertThat(specPage.getContent().get(0).defaultLanguageId()).isEqualTo(zhCnLanguageId);
        assertThat(specPage.getContent().get(0).defaultLanguageIdentifier()).isEqualTo("zh-CN");

        // 6. Verify System Default Namespace is created (if initializer bean is present)
        if (systemDefaultNamespaceInitializer.isPresent()) {
            assertThat(namespaceRepository.existsByShorthandPrefix("sys:")).isTrue();
        }
    }

    @Test
    void setup_registrySpecAttributes_applied() {
        InitWizardSpecAttributes specAttrs = new InitWizardSpecAttributes(
                "https://example.org/mdr",
                "ISO/IEC 10646",
                "ISO/IEC 11179-3:2023",
                DegreeOfConformance.STRICTLY_CONFORMING,
                "level-1",
                "label-1",
                "included",
                "excluded",
                "-0230",
                List.of("en-US"),
                List.of("URI"),
                "rcs-1");
        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "TEAM",
                null,
                "Spec Attr RA",
                null, null,
                true, true,
                null, null, null, null, null, null,
                true,
                null, null, null,
                specAttrs
        );

        initWizardService.setup(request, appUserId);

        var spec = registrySpecificationService.list(null, Pageable.ofSize(1)).getContent().get(0);
        assertThat(spec.webAddress()).isEqualTo("https://example.org/mdr");
        assertThat(spec.characterRepertoire()).isEqualTo("ISO/IEC 10646");
        assertThat(spec.standard()).isEqualTo("ISO/IEC 11179-3:2023");
        assertThat(spec.degreeOfConformance()).isEqualTo(DegreeOfConformance.STRICTLY_CONFORMING);
        assertThat(spec.conformanceLevel()).isEqualTo("level-1");
        assertThat(spec.defaultTimeZone()).isEqualTo("-0230");
        assertThat(spec.includedFeatures()).isEqualTo("included");
        assertThat(spec.excludedFeatures()).isEqualTo("excluded");
        assertThat(spec.referenceDocumentIdentifierKinds()).contains("URI");
        assertThat(spec.representationClassSchemeId()).isEqualTo("rcs-1");
        UUID enLangId = languageRepository.findByLanguageIdentifier("en-US").orElseThrow().getId();
        assertThat(spec.alternativeLanguageIds()).contains(enLangId);
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
                true,  // createNamespace（命名空间为必选，不能再跳过）
                null, null, null, null, null, null, null, null, null, null
        ,
            null
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
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        ,
            null
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
                .anyMatch(r -> contactAccountBindingService.accountIdOfContact(r.getId()).map(appUserId::equals).orElse(false));
        assertThat(hasRegistrar).isTrue();

        // 默认命名空间已创建
        assertThat(namespaceRepository.count()).isGreaterThanOrEqualTo(1);

        // 版本草稿已写入，系统仍未初始化（等 commit）
        assertThat(systemSettingService.getEdition().name()).isEqualTo("PERSONAL");
        assertThat(systemSettingService.isInitialized()).isFalse();
    }

    @Test
    void setup_explicitNamespaceFalse_rejected() {
        // 命名空间必选（设计 D7）：两个版本显式 createNamespace=false 均被拒绝，
        // 且不写入任何业务数据。
        InitWizardSetupRequest personalRequest = new InitWizardSetupRequest(
                "PERSONAL",
                null, null, null, null,
                null,
                false, // createNamespace
                null, null, null, null, null, null, null, null, null, null
        ,
            null
        );
        assertThatThrownBy(() -> initWizardService.setup(personalRequest, appUserId))
                .hasMessageContaining("命名空间");
        assertThat(registrationAuthorityRepository.count()).isZero();

        InitWizardSetupRequest teamRequest = new InitWizardSetupRequest(
                "TEAM",
                null,
                "Team RA",
                null, null, null,
                false, // createNamespace
                null, null, null, null, null, null, null, null, null, null
        ,
            null
        );
        assertThatThrownBy(() -> initWizardService.setup(teamRequest, appUserId))
                .hasMessageContaining("命名空间");
        assertThat(registrationAuthorityRepository.count()).isZero();
    }

    @Test
    void setup_explicitSpecFalse_rejected() {
        // 注册规范为必选：两个版本显式 createRegistrySpecification=false 均被拒绝，
        // 且不写入任何业务数据。
        InitWizardSetupRequest personalRequest = new InitWizardSetupRequest(
                "PERSONAL",
                null, null, null, null,
                null, null, null, null, null, null, null, null,
                false, // createRegistrySpecification
                null, null, null
        ,
            null
        );
        assertThatThrownBy(() -> initWizardService.setup(personalRequest, appUserId))
                .hasMessageContaining("注册规范");
        assertThat(registrationAuthorityRepository.count()).isZero();

        InitWizardSetupRequest teamRequest = new InitWizardSetupRequest(
                "TEAM",
                null, "Team RA", null, null,
                true, true,
                null, null, null, null, null, null,
                false, // createRegistrySpecification
                null, null, null
        ,
            null
        );
        assertThatThrownBy(() -> initWizardService.setup(teamRequest, appUserId))
                .hasMessageContaining("注册规范");
        assertThat(registrationAuthorityRepository.count()).isZero();
    }

    @Test
    void setup_cloudEdition_rejected() {
        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "CLOUD",
                null, "Some RA", null, null,
                true, true,
                null, null, null, null, null, null,
                null,
                null, null, null
        ,
            null
        );

        assertThatThrownBy(() -> initWizardService.setup(request, appUserId))
                .hasMessageContaining("不可用");
    }

    @Test
    void setup_legacyStrategyValue_rejected() {
        // 策略白名单收敛：UUID/SEQUENCE/TEMPLATE 等旧策略值一律拒绝
        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "TEAM",
                null, "Team RA", null, null,
                true, true,
                null,
                "UUID", // identifierStrategy：旧策略值
                null,
                null, null, null,
                null,
                null, null, null
        ,
            null
        );
        assertThatThrownBy(() -> initWizardService.setup(request, appUserId))
                .hasMessageContaining("段模板");
    }

    @Test
    void setup_inlineTemplate_publishedAsIs() {
        // 向导就地编排的模板（固定前缀 + 对象缩写 + 顺序号）直接成为已发布策略
        String inlineTemplate = """
                {"segments":[
                  {"type":"LITERAL","params":{"value":"WN"}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":5,"overflow":"REJECT"}}
                ]}""";
        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "TEAM",
                null, "Inline Template RA", null, null,
                true, true,
                null,
                "SEGMENT_TEMPLATE",
                inlineTemplate,
                null, null, null,
                null,
                null, null, null
        ,
            null
        );

        initWizardService.setup(request, appUserId);

        Namespace createdNs = namespaceRepository.findAll().stream()
                .filter(ns -> ns.getRegistrationAuthority() != null)
                .findFirst()
                .orElseThrow();
        IdentifierStrategyConfig config = identifierStrategyConfigRepository.findAll().stream()
                .filter(c -> c.getNamespace() != null && createdNs.getId().equals(c.getNamespace().getId()))
                .findFirst()
                .orElseThrow();
        assertThat(config.getStrategyType()).isEqualTo("SEGMENT_TEMPLATE");
        assertThat(config.getStatus().name()).isEqualTo("PUBLISHED");
        assertThat(config.getConfigJson()).contains("WN").contains("OBJECT_ABBREVIATION");
    }
}
