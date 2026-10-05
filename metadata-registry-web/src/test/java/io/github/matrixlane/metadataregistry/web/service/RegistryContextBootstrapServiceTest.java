package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSetupRequest;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.DefinitionContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.service.identifier.IdentifierInputPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:registry-context-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main",
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml"
})
@Transactional
@ActiveProfiles("test")
class RegistryContextBootstrapServiceTest {

    @Autowired
    private RegistryContextBootstrapService registryContextBootstrapService;

    @Autowired
    private InitWizardService initWizardService;

    @Autowired
    private SystemSettingService systemSettingService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RegistrationAuthorityRepository registrationAuthorityRepository;

    @Autowired
    private ContextRepository contextRepository;

    @Autowired
    private RegistrySpecificationRepository registrySpecificationRepository;

    @Autowired
    private AdministeredItemRepository administeredItemRepository;

    @Autowired
    private ScopedIdentifierRepository scopedIdentifierRepository;

    @Autowired
    private DesignationContextRepository designationContextRepository;

    @Autowired
    private DefinitionContextRepository definitionContextRepository;

    @Autowired
    private io.github.matrixlane.metadataregistry.web.repository.SubmissionRecordRepository submissionRecordRepository;

    @MockBean
    private IdentifierInputPolicyService identifierInputPolicyService;

    private UUID appUserId;

    @BeforeEach
    void setUp() {
        systemSettingService.setInitialized(false);

        Account adminUser = new Account();
        adminUser.setUsername("test-superadmin-" + UUID.randomUUID().toString().substring(0, 8));
        adminUser.setPasswordHash("password");
        adminUser.setSysRole(SystemRole.SUPER_ADMIN);
        appUserId = accountRepository.save(adminUser).getId();

        // 默认按 AUTO 透传；MANUAL_ONLY 用例单独覆盖
        when(identifierInputPolicyService.resolveIdentifier(any(Namespace.class), isNull(), eq("Context"), isNull()))
                .thenReturn("CTX.00001");
        when(identifierInputPolicyService.resolveVersion(any(Namespace.class), isNull()))
                .thenReturn("1.0.0");
        when(identifierInputPolicyService.resolveIdentifier(any(Namespace.class), eq("registry-context"), eq("Context"), isNull()))
                .thenReturn("registry-context");
        when(identifierInputPolicyService.resolveVersion(any(Namespace.class), eq("1.0")))
                .thenReturn("1.0");
    }

    @Test
    void ensureRegistryContext_firstCreation_createsWithFixedIdAndSelfReferences() {
        RegistrationAuthority ra = runWizardAndGetRa("AUTO_WITH_OVERRIDE", "AUTO_WITH_OVERRIDE", "SEMVER");
        Namespace ns = ra.getNamespace();

        Context ctx = registryContextBootstrapService.ensureRegistryContext(ra);

        assertThat(ctx).isNotNull();
        assertThat(ctx.getId()).isEqualTo(RegistryContextBootstrapService.REGISTRY_CONTEXT_ID);
        assertThat(contextRepository.count()).isEqualTo(1);

        // 2 指称 + 2 定义
        assertThat(ctx.getDesignations()).hasSize(2);
        assertThat(ctx.getDefinitions()).hasSize(2);

        // 4 条语境关系全部自指
        List<UUID> designationContextIds = designationContextRepository.findByContext_Id(ctx.getId()).stream()
                .map(dc -> dc.getContext().getId())
                .toList();
        List<UUID> definitionContextIds = definitionContextRepository.findByContext_Id(ctx.getId()).stream()
                .map(dc -> dc.getContext().getId())
                .toList();
        assertThat(designationContextIds).hasSize(2).containsOnly(ctx.getId());
        assertThat(definitionContextIds).hasSize(2).containsOnly(ctx.getId());

        // AdministeredItem 归属传入的 RA
        AdministeredItem ai = administeredItemRepository.findByItem_Id(ctx.getId()).orElseThrow();
        assertThat(ai.getRegistrationAuthority().getId()).isEqualTo(ra.getId());

        // 系统预置数据：注册状态为 Recorded（已由注册机构完整登记），管理状态为空
        // （不进入用户提交流程），无提交记录
        assertThat(ai.getRegistrationState().getRegistrationStatus())
                .isEqualTo(io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus.RECORDED);
        assertThat(ai.getRegistrationState().getAdministrativeStatus()).isNull();

        // scoped identifier 的 namespace = RA 命名空间；标识/版本由 AUTO 策略生成
        ScopedIdentifier si = scopedIdentifierRepository.findByItemId(ctx.getId()).get(0);
        assertThat(si.getNamespace().getId()).isEqualTo(ns.getId());
        assertThat(si.getIdentifier()).isEqualTo("CTX.00001");
        assertThat(si.getVersion()).isEqualTo("1.0.0");

        // spec.context 被回填
        RegistrySpecification spec = registrySpecificationRepository.findAll().get(0);
        assertThat(spec.getContext().getId()).isEqualTo(ctx.getId());
    }

    @Test
    void ensureRegistryContext_idempotent_secondCallDoesNotCreateNewRecords() {
        RegistrationAuthority ra = runWizardAndGetRa("AUTO_WITH_OVERRIDE", "AUTO_WITH_OVERRIDE", "SEMVER");

        Context first = registryContextBootstrapService.ensureRegistryContext(ra);
        Context second = registryContextBootstrapService.ensureRegistryContext(ra);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(contextRepository.count()).isEqualTo(1);
        assertThat(scopedIdentifierRepository.findByItemId(first.getId())).hasSize(1);
    }

    @Test
    void ensureRegistryContext_manualOnlyNamespace_usesFixedIdentifierAndVersion() {
        RegistrationAuthority ra = runWizardAndGetRa("MANUAL_ONLY", "MANUAL_ONLY", "SEQUENTIAL");
        Namespace ns = ra.getNamespace();

        Context ctx = registryContextBootstrapService.ensureRegistryContext(ra);

        ScopedIdentifier si = scopedIdentifierRepository.findByItemId(ctx.getId()).get(0);
        assertThat(si.getNamespace().getId()).isEqualTo(ns.getId());
        assertThat(si.getIdentifier()).isEqualTo("registry-context");
        assertThat(si.getVersion()).isEqualTo("1.0");
    }

    @Test
    void ensureRegistryContext_raWithoutNamespace_throwsIllegalStateException() {
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setNamespace(null);

        assertThatThrownBy(() -> registryContextBootstrapService.ensureRegistryContext(ra))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("registration authority has no namespace");
    }

    @Test
    void ensureRegistryContext_specContextAlreadySet_doesNotOverride() {
        RegistrationAuthority ra = runWizardAndGetRa("AUTO_WITH_OVERRIDE", "AUTO_WITH_OVERRIDE", "SEMVER");

        Context otherContext = new Context();
        otherContext = contextRepository.save(otherContext);

        RegistrySpecification spec = registrySpecificationRepository.findAll().get(0);
        spec.setContext(otherContext);
        registrySpecificationRepository.save(spec);

        Context ctx = registryContextBootstrapService.ensureRegistryContext(ra);

        assertThat(ctx.getId()).isEqualTo(RegistryContextBootstrapService.REGISTRY_CONTEXT_ID);
        RegistrySpecification reloaded = registrySpecificationRepository.findAll().get(0);
        assertThat(reloaded.getContext().getId()).isEqualTo(otherContext.getId());
    }

    @Test
    void backfillIfInitialized_notInitialized_doesNothing() {
        systemSettingService.setInitialized(false);

        registryContextBootstrapService.backfillIfInitialized();

        assertThat(contextRepository.existsById(RegistryContextBootstrapService.REGISTRY_CONTEXT_ID)).isFalse();
    }

    @Test
    void backfillIfInitialized_initializedAndMissing_createsRegistryContext() {
        RegistrationAuthority ra = runWizardAndGetRa("AUTO_WITH_OVERRIDE", "AUTO_WITH_OVERRIDE", "SEMVER");
        systemSettingService.setInitialized(true);

        registryContextBootstrapService.backfillIfInitialized();

        assertThat(contextRepository.existsById(RegistryContextBootstrapService.REGISTRY_CONTEXT_ID)).isTrue();
        Context ctx = contextRepository.findById(RegistryContextBootstrapService.REGISTRY_CONTEXT_ID).orElseThrow();
        AdministeredItem ai = administeredItemRepository.findByItem_Id(ctx.getId()).orElseThrow();
        assertThat(ai.getRegistrationAuthority().getId()).isEqualTo(ra.getId());
    }

    @Test
    void backfillIfInitialized_initializedAndExists_skipsCreation() {
        RegistrationAuthority ra = runWizardAndGetRa("AUTO_WITH_OVERRIDE", "AUTO_WITH_OVERRIDE", "SEMVER");
        registryContextBootstrapService.ensureRegistryContext(ra);
        systemSettingService.setInitialized(true);

        long before = contextRepository.count();
        registryContextBootstrapService.backfillIfInitialized();
        long after = contextRepository.count();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void backfillIfInitialized_existingDraftStatus_isNormalizedToEmpty() {
        RegistrationAuthority ra = runWizardAndGetRa("AUTO_WITH_OVERRIDE", "AUTO_WITH_OVERRIDE", "SEMVER");
        Context ctx = registryContextBootstrapService.ensureRegistryContext(ra);
        // 模拟早期版本创建的存量数据：管理状态为 DRAFT、注册状态为 INCOMPLETE
        AdministeredItem ai = administeredItemRepository.findByItem_Id(ctx.getId()).orElseThrow();
        ai.getRegistrationState().setAdministrativeStatus(
                io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus.DRAFT);
        ai.getRegistrationState().setRegistrationStatus(
                io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus.INCOMPLETE);
        administeredItemRepository.save(ai);
        systemSettingService.setInitialized(true);

        registryContextBootstrapService.backfillIfInitialized();

        AdministeredItem reloaded = administeredItemRepository.findByItem_Id(ctx.getId()).orElseThrow();
        assertThat(reloaded.getRegistrationState().getAdministrativeStatus()).isNull();
        assertThat(reloaded.getRegistrationState().getRegistrationStatus())
                .isEqualTo(io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus.RECORDED);
    }

    @Test
    void backfillIfInitialized_legacyZhDesignation_isRenamed() {
        RegistrationAuthority ra = runWizardAndGetRa("AUTO_WITH_OVERRIDE", "AUTO_WITH_OVERRIDE", "SEMVER");
        Context ctx = registryContextBootstrapService.ensureRegistryContext(ra);
        // 模拟早期版本存量数据：中文指称为旧文案「注册系统上下文」
        var zhDesignation = ctx.getDesignations().stream()
                .filter(d -> d.getSign() != null && "注册表上下文".equals(d.getSign().toString()))
                .findFirst()
                .orElseThrow();
        zhDesignation.setSign(
                io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign.of(
                        "注册系统上下文"));
        systemSettingService.setInitialized(true);

        registryContextBootstrapService.backfillIfInitialized();

        Context reloaded = contextRepository.findById(ctx.getId()).orElseThrow();
        assertThat(reloaded.getDesignations().stream().map(d -> d.getSign().toString()))
                .contains("注册表上下文")
                .doesNotContain("注册系统上下文");
    }

    @Test
    void backfillIfInitialized_specContextNull_isBackfilled() {
        RegistrationAuthority ra = runWizardAndGetRa("AUTO_WITH_OVERRIDE", "AUTO_WITH_OVERRIDE", "SEMVER");
        Context ctx = registryContextBootstrapService.ensureRegistryContext(ra);
        // 模拟存量异常：注册规范未关联默认上下文
        RegistrySpecification spec = registrySpecificationRepository.findAll().stream().findFirst().orElseThrow();
        spec.setContext(null);
        registrySpecificationRepository.save(spec);
        systemSettingService.setInitialized(true);

        registryContextBootstrapService.backfillIfInitialized();

        RegistrySpecification reloaded = registrySpecificationRepository.findAll().stream().findFirst().orElseThrow();
        assertThat(reloaded.getContext()).isNotNull();
        assertThat(reloaded.getContext().getId()).isEqualTo(ctx.getId());
    }

    @Test
    void backfillIfInitialized_initializedWithoutAnyPrimary_marksEarliestAuthority() {
        RegistrationAuthority wizardRa = runWizardAndGetRa("AUTO_WITH_OVERRIDE", "AUTO_WITH_OVERRIDE", "SEMVER");
        // 向导创建的机构现即 Primary；本用例模拟迁移前存量状态（全库无任何 Primary），
        // 故显式清除该标志
        wizardRa.setPrimary(false);
        registrationAuthorityRepository.save(wizardRa);
        // 模拟存量库补充机构（旧库无 Primary 概念，is_primary 均为 false）
        RegistrationAuthority legacy = registrationAuthorityRepository.save(newLegacyAuthority("LEGACY-RA-1"));
        systemSettingService.setInitialized(true);

        registryContextBootstrapService.backfillIfInitialized();

        // "最早创建"= id（UUID）字典序最小，与三方言 SQL MIN(id) 一致（LanguageIdentifierResolver 口径）
        UUID earliestId = java.util.stream.Stream.of(wizardRa.getId(), legacy.getId())
                .min(Comparator.comparing(UUID::toString))
                .orElseThrow();
        UUID otherId = earliestId.equals(wizardRa.getId()) ? legacy.getId() : wizardRa.getId();
        assertThat(registrationAuthorityRepository.findById(earliestId).orElseThrow().isPrimary()).isTrue();
        assertThat(registrationAuthorityRepository.findById(otherId).orElseThrow().isPrimary()).isFalse();
    }

    @Test
    void backfillIfInitialized_alreadyHasPrimary_leavesFlagsUnchanged() {
        RegistrationAuthority ra1 = registrationAuthorityRepository.save(newLegacyAuthority("LEGACY-RA-1"));
        RegistrationAuthority ra2 = registrationAuthorityRepository.save(newLegacyAuthority("LEGACY-RA-2"));
        // 故意把非最早的机构标为 Primary：guard 生效则两次启动都不会改动任何标志
        RegistrationAuthority kept = ra1.getId().toString().compareTo(ra2.getId().toString()) < 0 ? ra2 : ra1;
        kept.setPrimary(true);
        registrationAuthorityRepository.save(kept);
        systemSettingService.setInitialized(true);

        registryContextBootstrapService.backfillIfInitialized();
        registryContextBootstrapService.backfillIfInitialized();

        assertThat(registrationAuthorityRepository.findById(ra1.getId()).orElseThrow().isPrimary())
                .isEqualTo(ra1.getId().equals(kept.getId()));
        assertThat(registrationAuthorityRepository.findById(ra2.getId()).orElseThrow().isPrimary())
                .isEqualTo(ra2.getId().equals(kept.getId()));
    }

    @Test
    void backfillIfInitialized_registryContextExistsButNoPrimary_stillBackfillsFlag() {
        RegistrationAuthority ra = runWizardAndGetRa("AUTO_WITH_OVERRIDE", "AUTO_WITH_OVERRIDE", "SEMVER");
        registryContextBootstrapService.ensureRegistryContext(ra);
        systemSettingService.setInitialized(true);

        registryContextBootstrapService.backfillIfInitialized();

        assertThat(registrationAuthorityRepository.findById(ra.getId()).orElseThrow().isPrimary()).isTrue();
    }

    @Test
    void backfillIfInitialized_notInitialized_leavesPrimaryUnset() {
        RegistrationAuthority ra = registrationAuthorityRepository.save(newLegacyAuthority("LEGACY-RA-1"));
        systemSettingService.setInitialized(false);

        registryContextBootstrapService.backfillIfInitialized();

        assertThat(registrationAuthorityRepository.findById(ra.getId()).orElseThrow().isPrimary()).isFalse();
    }

    @Test
    void backfillIfInitialized_initializedWithoutAnyAuthority_keepsSystemIntact() {
        systemSettingService.setInitialized(true);

        registryContextBootstrapService.backfillIfInitialized();

        assertThat(registrationAuthorityRepository.findAll()).isEmpty();
    }

    @Test
    void ensureRegistryContext_noSubmissionRecordIsCreated() {
        RegistrationAuthority ra = runWizardAndGetRa("AUTO_WITH_OVERRIDE", "AUTO_WITH_OVERRIDE", "SEMVER");
        Context ctx = registryContextBootstrapService.ensureRegistryContext(ra);

        AdministeredItem ai = administeredItemRepository.findByItem_Id(ctx.getId()).orElseThrow();
        assertThat(submissionRecordRepository.findAll().stream()
                .filter(sub -> sub.getRegisteredItems().stream()
                        .anyMatch(ri -> ri.getId().equals(ai.getId()))))
                .isEmpty();
    }

    private RegistrationAuthority newLegacyAuthority(String organizationIdentifier) {
        RegistrationAuthority ra = new RegistrationAuthority();
        io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier rai =
                new io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier();
        rai.setInternationalCodeDesignator("ISO");
        rai.setOrganizationIdentifier(organizationIdentifier);
        ra.setRegistrationAuthorityIdentifier(rai);
        return ra;
    }

    private RegistrationAuthority runWizardAndGetRa(String identifierInputMode,
                                                    String versionInputMode,
                                                    String versionPolicy) {
        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "PERSONAL",                 "Registry Context Test RA",
                null,
                null,
                null, null, null,
                false,
                true,
                null,
                "SEGMENT_TEMPLATE",
                null,
                identifierInputMode,
                versionInputMode,
                versionPolicy,
                true,
                null, null, null,
                null,
                null, null  // adminIndividualName, adminRoleName（PERSONAL 有兜底）
        );
        initWizardService.setup(request, appUserId);

        return registrationAuthorityRepository.findAll().stream()
                .filter(ra -> !ra.getName().isEmpty())
                .findFirst()
                .orElseThrow();
    }
}
