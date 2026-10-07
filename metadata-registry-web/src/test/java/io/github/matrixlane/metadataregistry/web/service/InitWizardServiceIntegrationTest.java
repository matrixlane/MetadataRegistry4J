package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfig;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardCommitRequest;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSetupRequest;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSpecAttributes;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.DegreeOfConformance;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
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
        // 覆盖 mapping-resources：用户主目录 application-local.yml 可能注入
        // orm-team.xml（SQL Server 专用）；内存 SQLite 测试改用 orm-sqlite.xml（EXT 物理名 ext_ 前缀），否则与标准表同名冲突。
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml"
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
    private io.github.matrixlane.metadataregistry.web.repository.IndividualRepository individualRepository;
    @Autowired
    private ContactAccountBindingService contactAccountBindingService;

    @Autowired
    private RegistrySpecificationService registrySpecificationService;

    @Autowired
    private RegistrationAuthorityRepository registrationAuthorityRepository;

    @Autowired
    private io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityExtensionRepository registrationAuthorityExtensionRepository;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private IdentifierStrategyConfigRepository identifierStrategyConfigRepository;

    @Autowired
    private LanguageIdentificationRepository languageRepository;

    @Autowired
    private ContextRepository contextRepository;

    @Autowired
    private RegistrySpecificationRepository registrySpecificationRepository;

    @Autowired
    private ScopedIdentifierRepository scopedIdentifierRepository;

    @Autowired
    private Optional<SystemDefaultNamespaceInitializer> systemDefaultNamespaceInitializer;

    private UUID appUserId;

    @BeforeEach
    void setUp() {
        // Ensure system setting initialized is false before test
        systemSettingService.setInitialized(false);

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
                "TEAM",                 "Integration Test RA",
                "Integration Test RA English",
                "ra@integration.com",
                "0000",                       // registrationAuthorityIcd
                "RA",                         // registrationAuthorityOrganizationIdentifier（断言 inMemory == "RA"）
                List.of("zh-CN"),             // registrationAuthorityDocumentLanguages
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
            null,
            null, "注册管理员"  // adminIndividualName, adminRoleName
        );

        initWizardService.setup(request, appUserId);

        // 1. setup() must NOT flip system.initialized — that happens in commit()
        assertThat(systemSettingService.isInitialized()).isFalse();

        // 2. Verify RegistrationAuthority is created
        List<RegistrationAuthority> authorities = registrationAuthorityRepository.findAll();
        RegistrationAuthority createdRa = authorities.stream()
                .filter(ra -> !ra.getName().isEmpty() && "Integration Test RA".equals(ra.getName().get(0).getValue()))
                .findFirst()
                .orElse(null);
        assertThat(createdRa).isNotNull();
        // 初始化创建的注册机构即 Primary 注册机构（Primary 存于 ext 卫星表）
        assertThat(registrationAuthorityExtensionRepository.findById(createdRa.getId())
                .map(io.github.matrixlane.metadataregistry.ext.registration.RegistrationAuthorityExtension::isPrimary)
                .orElse(false)).isTrue();
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

        // 4. Verify Super Admin User is assigned Registrar contact（身份由 Registrar 子类实例
        //    确定；Role 按提交的 adminRoleName 创建并关联，所属组织恒为本次创建的 RA）
        List<Registrar> registrars = registrarRepository.findAll();
        boolean registrarAssigned = registrars.stream()
                .anyMatch(r -> contactAccountBindingService.accountIdOfContact(r.getId()).map(appUserId::equals).orElse(false) &&
                        r.getRole() != null && "注册管理员".equals(r.getRole().getRoleName()) &&
                        r.getOrganization() != null && createdRa.getId().equals(r.getOrganization().getId()) &&
                        r.getRegistrationAuthority() != null && createdRa.getId().equals(r.getRegistrationAuthority().getId()));
        assertThat(registrarAssigned).isTrue();

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
        // 5.1a 注册规范在 setup 阶段先行创建，且不引用注册机构（context 回填仅发生在
        // commit 之后的 Registry Context 引导流程）
        var specEntityBeforeCommit = registrySpecificationRepository.findAll().get(0);
        assertThat(specEntityBeforeCommit.getContext()).isNull();

        // 5.2 setup 阶段不创建 Registry Context（其创建需要独立事务，须待 setup 数据提交后
        // 在 commit() 中执行）
        assertThat(contextRepository.findById(RegistryContextBootstrapService.REGISTRY_CONTEXT_ID)).isEmpty();

        // 5.3 commit 完成初始化：Registry Context 创建（固定 id、自指、spec.context 回填）
        initWizardService.commit(new InitWizardCommitRequest("reg-ctx-int-123", "reg-ctx-int-123"));
        initWizardService.createRegistryContextAfterInitialization();
        assertThat(systemSettingService.isInitialized()).isTrue();

        var registryContext = contextRepository.findById(RegistryContextBootstrapService.REGISTRY_CONTEXT_ID).orElseThrow();
        assertThat(registryContext.getId()).isEqualTo(RegistryContextBootstrapService.REGISTRY_CONTEXT_ID);
        assertThat(registryContext.getDesignations()).hasSize(2);
        assertThat(registryContext.getDefinitions()).hasSize(2);

        var spec = registrySpecificationRepository.findAll().get(0);
        assertThat(spec.getContext()).isNotNull();
        assertThat(spec.getContext().getId()).isEqualTo(registryContext.getId());

        var registryContextIdentifiers = scopedIdentifierRepository.findByItemId(registryContext.getId());
        assertThat(registryContextIdentifiers).hasSize(1);
        var registryContextSi = registryContextIdentifiers.get(0);
        assertThat(registryContextSi.getNamespace().getId()).isEqualTo(createdNs.getId());
        assertThat(registryContextSi.getIdentifier()).isEqualTo("CTX.00001");
        assertThat(registryContextSi.getVersion()).isNotBlank();

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
                "TEAM",                 "Spec Attr RA",
                null, null,
                "0000", "RA", List.of("en-US"),  // icd, organizationIdentifier, documentLanguages
                true, true,
                null, null, null, null, null, null,
                true,
                null, null, null,
                specAttrs,
                null, "注册管理员"  // adminIndividualName, adminRoleName
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
                "PERSONAL",                 "Integration Test RA",
                null,
                null,
                null, null, null,         // icd, organizationIdentifier, documentLanguages（PERSONAL 取服务默认值）
                false, // selfAsRegistrar
                true,  // createNamespace（命名空间为必选，不能再跳过）
                null, null, null, null, null, null, null, null, null, null
        ,
            null,
            null, null  // adminIndividualName, adminRoleName
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
                "PERSONAL", null, null, null,
                null, null, null,         // icd, organizationIdentifier, documentLanguages（全部取服务默认值）
                null, null, null, null, null, null, null, null, null,
                null, null, null          // registryName, defaultLanguage, registryDescription
        ,
            null,
            null, null  // adminIndividualName, adminRoleName
        );

        initWizardService.setup(request, appUserId);

        List<RegistrationAuthority> authorities = registrationAuthorityRepository.findAll();
        RegistrationAuthority ra = authorities.stream()
                .filter(a -> !a.getName().isEmpty() && "默认注册机构".equals(a.getName().get(0).getValue()))
                .findFirst()
                .orElse(null);
        assertThat(ra).isNotNull();

        // 管理员自动成为注册员
        List<Registrar> registrars = registrarRepository.findAll();
        boolean hasRegistrar = registrars.stream()
                .anyMatch(r -> contactAccountBindingService.accountIdOfContact(r.getId()).map(appUserId::equals).orElse(false));
        assertThat(hasRegistrar).isTrue();

        // 姓名留空：不得创建任何 Individual（含"注册管理员"兜底个体），Registrar 无 Individual 关联
        assertThat(individualRepository.count()).isZero();
        Registrar adminRegistrar = registrars.stream()
                .filter(r -> contactAccountBindingService.accountIdOfContact(r.getId()).map(appUserId::equals).orElse(false))
                .findFirst().orElseThrow();
        assertThat(adminRegistrar.getIndividual()).isNull();
        // 角色兜底"注册管理员"仍生效
        assertThat(adminRegistrar.getRole()).isNotNull();
        assertThat(adminRegistrar.getRole().getRoleName()).isEqualTo("注册管理员");

        // 默认命名空间已创建
        assertThat(namespaceRepository.count()).isGreaterThanOrEqualTo(1);

        // sys: 系统默认命名空间 NamingAuthority 挂接到新建注册机构（不再使用系统组织）
        Namespace sysNs = namespaceRepository.findByShorthandPrefix("sys:").orElseThrow();
        assertThat(sysNs.getNamingAuthority()).isNotNull();
        assertThat(sysNs.getNamingAuthority().getId()).isEqualTo(ra.getId());

        // 版本草稿已写入，系统仍未初始化（等 commit）
        assertThat(systemSettingService.getEdition().name()).isEqualTo("PERSONAL");
        assertThat(systemSettingService.isInitialized()).isFalse();
    }

    @Test
    void setup_explicitNamespaceFalse_rejected() {
        // 命名空间必选（设计 D7）：两个版本显式 createNamespace=false 均被拒绝，
        // 且不写入任何业务数据。
        InitWizardSetupRequest personalRequest = new InitWizardSetupRequest(
                "PERSONAL", null, null, null,
                null, null, null,        // icd, organizationIdentifier, documentLanguages
                null,                    // selfAsRegistrar
                false, // createNamespace
                null, null, null, null, null, null, null,
                null, null, null         // registryName, defaultLanguage, registryDescription
        ,
            null,
            null, null  // adminIndividualName, adminRoleName
        );
        assertThatThrownBy(() -> initWizardService.setup(personalRequest, appUserId))
                .hasMessageContaining("命名空间");
        assertThat(registrationAuthorityRepository.count()).isZero();

        InitWizardSetupRequest teamRequest = new InitWizardSetupRequest(
                "TEAM",                 "Team RA",
                null, null,             // englishName, email
                "0000", "RA", List.of("zh-CN"),  // icd, organizationIdentifier, documentLanguages
                null,                   // selfAsRegistrar
                false, // createNamespace
                null, null, null, null, null, null, null,
                null, null, null         // registryName, defaultLanguage, registryDescription
        ,
            null,
            null, null  // adminIndividualName, adminRoleName
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
                "PERSONAL", null, null, null,
                null, null, null,        // icd, organizationIdentifier, documentLanguages
                null, null, null, null, null, null, null, null,
                false, // createRegistrySpecification
                null, null, null
        ,
            null,
            null, null  // adminIndividualName, adminRoleName
        );
        assertThatThrownBy(() -> initWizardService.setup(personalRequest, appUserId))
                .hasMessageContaining("注册规范");
        assertThat(registrationAuthorityRepository.count()).isZero();

        InitWizardSetupRequest teamRequest = new InitWizardSetupRequest(
                "TEAM", "Team RA", null, null,
                "0000", "RA", List.of("zh-CN"),  // icd, organizationIdentifier, documentLanguages
                true, true,
                null, null, null, null, null, null,
                false, // createRegistrySpecification
                null, null, null
        ,
            null,
            null, "注册管理员"  // adminIndividualName, adminRoleName
        );
        assertThatThrownBy(() -> initWizardService.setup(teamRequest, appUserId))
                .hasMessageContaining("注册规范");
        assertThat(registrationAuthorityRepository.count()).isZero();
    }

    @Test
    void setup_cloudEdition_rejected() {
        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "CLOUD",
                "Some RA", null, null,
                null, null, null,        // icd, organizationIdentifier, documentLanguages（与该用例否定版本无关）
                true, true,
                null, null, null, null, null, null,
                null,
                null, null, null
        ,
            null,
            null, null  // adminIndividualName, adminRoleName
        );

        assertThatThrownBy(() -> initWizardService.setup(request, appUserId))
                .hasMessageContaining("不可用");
    }

    @Test
    void setup_legacyStrategyValue_rejected() {
        // 策略白名单收敛：UUID/SEQUENCE/TEMPLATE 等旧策略值一律拒绝
        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "TEAM", "Team RA", null, null,
                "0000", "RA", List.of("zh-CN"),  // icd, organizationIdentifier, documentLanguages
                true, true,
                null,
                "UUID", // identifierStrategy：旧策略值
                null,
                null, null, null,
                null,
                null, null, null
        ,
            null,
            null, "注册管理员"  // adminIndividualName, adminRoleName
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
                "TEAM", "Inline Template RA", null, null,
                "0000", "RA", List.of("zh-CN"),  // icd, organizationIdentifier, documentLanguages
                true, true,
                null,
                "SEGMENT_TEMPLATE",
                inlineTemplate,
                null, null, null,
                null,
                null, null, null
        ,
            null,
            null, "注册管理员"  // adminIndividualName, adminRoleName
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

    /** TEAM 版注册管理员三要素：一个基础请求构造，仅供本组三要素用例复用。 */
    private InitWizardSetupRequest teamSetupRequest(Boolean selfAsRegistrar,
                                                    String adminIndividualName,
                                                    String adminRoleName) {
        return new InitWizardSetupRequest(
                "TEAM", "Three Elements RA", null, null,
                "0000", "RA", List.of("zh-CN"),
                selfAsRegistrar, true,
                null,
                "SEGMENT_TEMPLATE",
                null, null, null, null,
                null,
                null, null, null,
                null,
                adminIndividualName, adminRoleName);
    }

    private RegistrationAuthority findCreatedRa() {
        return registrationAuthorityRepository.findAll().stream()
                .filter(ra -> ra.getName() != null && !ra.getName().isEmpty()
                        && "Three Elements RA".equals(ra.getName().get(0).getValue()))
                .findFirst()
                .orElseThrow();
    }

    /** 初始化阶段不存在任何可选组织：请求不带所属组织也必须成功完成。 */
    @Test
    void setup_teamEdition_withoutOrganizationField_succeeds() {
        initWizardService.setup(teamSetupRequest(true, null, "数据治理主管"), appUserId);
        assertThat(findCreatedRa()).isNotNull();
        assertThat(registrarRepository.findAll()).isNotEmpty();
    }

    /** Registrar 的组织恒等于本次创建的 Registration_Authority（RegistrationAuthority extends Organization）。 */
    @Test
    void setup_teamEdition_registrarOrganizationIsRegistrationAuthority() {
        initWizardService.setup(teamSetupRequest(true, "李雷", "数据治理主管"), appUserId);

        UUID raId = findCreatedRa().getId();
        List<Registrar> registrars = registrarRepository.findAll();
        assertThat(registrars).hasSize(1);
        Registrar registrar = registrars.get(0);
        assertThat(registrar.getOrganization()).isNotNull();
        assertThat(registrar.getOrganization().getId()).isEqualTo(raId);
        assertThat(registrar.getRole()).isNotNull();
        assertThat(registrar.getIndividual()).isNotNull();
    }

    /** 关闭「设为注册人员」即不创建 Registrar，不应要求其要素，也不得因此失败。 */
    @Test
    void setup_teamEdition_selfAsRegistrarOff_skipsElementValidation() {
        initWizardService.setup(teamSetupRequest(false, null, null), appUserId);

        assertThat(findCreatedRa()).isNotNull();
        assertThat(registrarRepository.findAll()).isEmpty();
    }

    /**
     * API 侧省略该开关与显式 false 同义：均不创建 Registrar，且不校验其要素。
     * 界面始终显式提交并缺省为开启，该默认值属前端呈现，不改变 API 契约。
     */
    @Test
    void setup_teamEdition_selfAsRegistrarNull_treatedAsOff() {
        initWizardService.setup(teamSetupRequest(null, null, null), appUserId);

        assertThat(findCreatedRa()).isNotNull();
        assertThat(registrarRepository.findAll()).isEmpty();
    }
}
