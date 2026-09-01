package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineContextEntry;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamingConventionRepository;
import io.github.matrixlane.metadataregistry.web.service.identifier.IdentifierInputPolicyService;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionEngine;
import io.github.matrixlane.metadataregistry.web.service.namespace.NamespaceConstraintValidator;
import io.github.matrixlane.metadataregistry.web.seed.SystemDefaultNamespaceInitializer;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemAdministrationServiceTest {

    @Mock private LanguageIdentificationRepository languageRepository;
    @Mock private ReferenceDocumentRepository referenceDocumentRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository namespaceRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository registrationAuthorityRepository;
    @Mock private ContextRepository contextRepository;
    @Mock private NamingConventionEngine namingConventionEngine;
    @Mock private IdentifierInputPolicyService identifierInputPolicyService;
    @Mock private NamingConventionRepository namingConventionRepository;
    @Mock private NamespaceConstraintValidator namespaceConstraintValidator;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository scopedIdentifierRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository administeredItemRepository;
    @Mock private ActiveRegistrarService activeRegistrarService;
    @Mock private RegistrySpecificationRepository registrySpecificationRepository;

    @InjectMocks
    private ItemAdministrationService itemAdministrationService;

    private UUID itemId;
    private UUID languageId;
    private UUID contextId;
    private LanguageIdentification lang;
    private Item item;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        languageId = UUID.randomUUID();
        contextId = UUID.randomUUID();

        lang = new LanguageIdentification();
        lang.setId(languageId);
        lang.setLanguageIdentifier("zh-CN");

        item = new TestItem();
        item.setId(itemId);

        // 缺省按"透传手动值"裁决，个别用例可覆盖
        lenient().when(identifierInputPolicyService.resolveIdentifier(any(), any(), any(), any()))
                .thenAnswer(i -> i.getArgument(1));
        lenient().when(identifierInputPolicyService.resolveVersion(any(), any()))
                .thenAnswer(i -> {
                    String v = i.getArgument(1);
                    return v == null || v.isBlank() ? "1.0.0" : v;
                });
        // 默认注册规范无默认语言；需要默认语言的用例单独覆盖
        lenient().when(registrySpecificationRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void applyDesignations_shouldSucceed() {
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));
        Context ctx = new Context();
        ctx.setId(contextId);
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(ctx));

        TextLine line = new TextLine(languageId, "患者姓名", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.PREFERRED)));

        itemAdministrationService.applyDesignations(item, List.of(line));

        assertThat(item.getDesignations()).hasSize(1);
        assertThat(item.getDesignations().get(0).getSign().getValue()).isEqualTo("患者姓名");
    }

    @Test
    void applyDesignations_shouldRejectWhenNamingConventionFails() {
        when(namingConventionEngine.validate(any())).thenReturn(List.of(
                io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionRuleResult.fail(
                        "length", "名称不能为空")
        ));

        TextLine line = new TextLine(languageId, "", null,
                List.of(new TextLineContextEntry(UUID.randomUUID(), Acceptability.ADMITTED)));

        assertThatThrownBy(() -> itemAdministrationService.applyDesignations(item, List.of(line)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("命名规范校验失败");
    }

    @Test
    void applyDesignations_shouldRejectDuplicatePreferred() {
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        TextLine line1 = new TextLine(languageId, "首选名称1", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.PREFERRED)));
        TextLine line2 = new TextLine(languageId, "首选名称2", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.PREFERRED)));

        assertThatThrownBy(() -> itemAdministrationService.applyDesignations(item, List.of(line1, line2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能有一个首选指称");
    }

    @Test
    void applyDesignations_withNamespaceId_shouldApplyNamingConventions() {
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        UUID namespaceId = UUID.randomUUID();
        UUID ncId = UUID.randomUUID();
        io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention nc =
                new io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention();
        nc.setId(ncId);
        when(namingConventionRepository.findAllById(List.of(ncId))).thenReturn(List.of(nc));

        Context ctx = new Context();
        ctx.setId(contextId);
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(ctx));

        TextLine line = new TextLine(languageId, "患者姓名", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.ADMITTED)), List.of(ncId));

        itemAdministrationService.applyDesignations(item, List.of(line), namespaceId);

        assertThat(item.getDesignations()).hasSize(1);
        assertThat(item.getDesignations().get(0).getNamingConventions())
                .hasSize(1)
                .extracting("id")
                .containsExactly(ncId);
        verify(namespaceConstraintValidator).validateDesignation(eq(namespaceId), any(Designation.class), eq(null));
    }

    @Test
    void applyDesignations_withMandatoryNamespace_shouldRejectMissingNamingConvention() {
        UUID namespaceId = UUID.randomUUID();
        Context ctx = new Context();
        ctx.setId(contextId);
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(ctx));

        TextLine line = new TextLine(languageId, "患者姓名", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.ADMITTED)), null);
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        itemAdministrationService.applyDesignations(item, List.of(line), namespaceId);

        verify(namespaceConstraintValidator).validateDesignation(eq(namespaceId), any(Designation.class), eq(null));
    }

    @Test
    void applyDefinitions_shouldSucceed() {
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));
        Context ctx = new Context();
        ctx.setId(contextId);
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(ctx));

        TextLine line = new TextLine(languageId, "患者的唯一标识", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.ADMITTED)));

        itemAdministrationService.applyDefinitions(item, List.of(line));

        assertThat(item.getDefinitions()).hasSize(1);
        assertThat(item.getDefinitions().get(0).getText().getValue()).isEqualTo("患者的唯一标识");
    }

    @Test
    void applyDefinitions_shouldReplaceExisting() {
        Definition existing = new Definition();
        existing.setId(UUID.randomUUID());
        existing.setText(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text.of("旧定义"));
        existing.setLanguage(lang);
        existing.setItem(item);
        item.getDefinitions().add(existing);

        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));
        Context ctx = new Context();
        ctx.setId(contextId);
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(ctx));

        TextLine line = new TextLine(languageId, "新定义", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.ADMITTED)));

        itemAdministrationService.applyDefinitions(item, List.of(line));

        assertThat(item.getDefinitions()).hasSize(1);
        assertThat(item.getDefinitions().get(0).getText().getValue()).isEqualTo("新定义");
    }

    @Test
    void applyDesignations_shouldRejectIsolatedLineWithoutException() {
        TextLine line = new TextLine(languageId, "孤立指称", null, null, null, false);

        assertThatThrownBy(() -> itemAdministrationService.applyDesignations(item, List.of(line)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("既没有上下文也未标记为例外");
    }

    @Test
    void applyDesignations_shouldAllowIsolatedLineWithException() {
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        TextLine line = new TextLine(languageId, "例外指称", null, null, null, true);

        itemAdministrationService.applyDesignations(item, List.of(line));

        assertThat(item.getDesignations()).hasSize(1);
    }

    @Test
    void ensureAdministeredItem_shouldUseManualIdentifier() {
        UUID namespaceId = UUID.randomUUID();
        Namespace ns = new Namespace();
        ns.setId(namespaceId);
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(ns));

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                namespaceId, "MANUAL-123", "1.0.0", List.of(), List.of()
        );

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai).isNotNull();
        assertThat(ai.getScopedIdentifiers()).hasSize(1);
        assertThat(ai.getScopedIdentifiers().get(0).getIdentifier()).isEqualTo("MANUAL-123");
    }

    @Test
    void ensureAdministeredItem_shouldAutoGenerateIdentifierWhenEmpty() {
        UUID namespaceId = UUID.randomUUID();
        Namespace ns = new Namespace();
        ns.setId(namespaceId);
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(ns));
        when(identifierInputPolicyService.resolveIdentifier(eq(ns), eq(""), any(), any()))
                .thenReturn("AUTO-999");

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                namespaceId, "", "1.0.0", List.of(), List.of()
        );

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai).isNotNull();
        assertThat(ai.getScopedIdentifiers()).hasSize(1);
        assertThat(ai.getScopedIdentifiers().get(0).getIdentifier()).isEqualTo("AUTO-999");
        verify(identifierInputPolicyService).resolveIdentifier(eq(ns), eq(""), any(), any());
    }

    @Test
    void ensureAdministeredItem_noRaNamespace_blocked() {
        // 目标为注册机构关联命名空间，但系统中尚无任何注册机构关联命名空间
        // （初始化时跳过了命名空间设置）→ 阻止创建自有数据标准。
        UUID namespaceId = UUID.randomUUID();
        Namespace ns = new Namespace();
        ns.setId(namespaceId);
        ns.setRegistrationAuthority(new RegistrationAuthority());
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(false);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                namespaceId, "MANUAL-123", "1.0.0", List.of(), List.of()
        );

        assertThatThrownBy(() -> itemAdministrationService.ensureAdministeredItem(item, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("命名空间");
    }

    @Test
    void ensureAdministeredItem_systemDefaultNamespace_blockedWhenNoRaNamespace() {
        // system-default 命名空间不计入前置条件：向其中创建自有数据标准同样被阻止。
        UUID namespaceId = UUID.randomUUID();
        Namespace ns = new Namespace();
        ns.setId(namespaceId);
        ns.setSchemeReference(Sign.of(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_SCHEME));
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(false);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                namespaceId, "MANUAL-123", "1.0.0", List.of(), List.of()
        );

        assertThatThrownBy(() -> itemAdministrationService.ensureAdministeredItem(item, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("命名空间");
    }

    @Test
    void ensureAdministeredItem_externalNamespace_allowedWhenNoRaNamespace() {
        // 外部命名空间（无关联注册机构）用于导入/编目外部数据标准，不受门槛限制。
        UUID namespaceId = UUID.randomUUID();
        Namespace ns = new Namespace();
        ns.setId(namespaceId);
        ns.setSchemeReference(Sign.of("urn:example:external-standard"));
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(ns));

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                namespaceId, "EXT-001", "1.0.0", List.of(), List.of()
        );

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai).isNotNull();
        assertThat(ai.getScopedIdentifiers()).hasSize(1);
    }

    @Test
    void ensureAdministeredItem_raNamespaceExists_passes() {
        // 前置条件满足（存在注册机构关联命名空间）→ 正常创建。
        UUID namespaceId = UUID.randomUUID();
        Namespace ns = new Namespace();
        ns.setId(namespaceId);
        ns.setRegistrationAuthority(new RegistrationAuthority());
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                namespaceId, "MANUAL-123", "1.0.0", List.of(), List.of()
        );

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai).isNotNull();
        assertThat(ai.getScopedIdentifiers()).hasSize(1);
    }

    @jakarta.persistence.Entity
    static class TestItem extends Item {}

    // ===== active-registrar-ownership：注册权限与所有权归属 =====


    @Test
    void applyDesignations_shouldResolveDefaultLanguageWhenLanguageIdOmitted() {
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(registrySpecificationRepository.findAll()).thenReturn(List.of(defaultRegistrySpecification()));
        Context ctx = new Context();
        ctx.setId(contextId);
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(ctx));

        TextLine line = new TextLine(null, "default-language-designation", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.PREFERRED)));

        itemAdministrationService.applyDesignations(item, List.of(line));

        assertThat(item.getDesignations()).hasSize(1);
        assertThat(item.getDesignations().get(0).getLanguage()).isSameAs(lang);
        assertThat(item.getDesignations().get(0).getSign().getValue()).isEqualTo("default-language-designation");
    }

    @Test
    void applyDefinitions_shouldResolveDefaultLanguageWhenLanguageIdOmitted() {
        when(registrySpecificationRepository.findAll()).thenReturn(List.of(defaultRegistrySpecification()));
        Context ctx = new Context();
        ctx.setId(contextId);
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(ctx));

        TextLine line = new TextLine(null, "default-language-definition", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.PREFERRED)));

        itemAdministrationService.applyDefinitions(item, List.of(line));

        assertThat(item.getDefinitions()).hasSize(1);
        assertThat(item.getDefinitions().get(0).getLanguage()).isSameAs(lang);
        assertThat(item.getDefinitions().get(0).getText().getValue()).isEqualTo("default-language-definition");
    }

    @Test
    void applyDesignations_shouldRejectWhenNoDefaultLanguageConfigured() {
        when(namingConventionEngine.validate(any())).thenReturn(List.of());

        TextLine line = new TextLine(null, "无默认语言指称", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.PREFERRED)));

        assertThatThrownBy(() -> itemAdministrationService.applyDesignations(item, List.of(line)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置注册表默认语言");
    }

    @Test
    void applyDefinitions_shouldRejectWhenNoDefaultLanguageConfigured() {
        TextLine line = new TextLine(null, "无默认语言定义", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.PREFERRED)));

        assertThatThrownBy(() -> itemAdministrationService.applyDefinitions(item, List.of(line)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置注册表默认语言");
    }

    private RegistrySpecification defaultRegistrySpecification() {
        RegistrySpecification rs = new RegistrySpecification();
        rs.setId(UUID.randomUUID());
        rs.setDefaultLanguage(lang);
        return rs;
    }

    private RegistrationAuthority newRa() {
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        return ra;
    }

    private Namespace raNamespace(RegistrationAuthority ra) {
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        ns.setRegistrationAuthority(ra);
        return ns;
    }

    @Test
    void ensureAdministeredItem_create_assignsActiveRegistrarOwnership() {
        RegistrationAuthority ra = newRa();
        Namespace ns = raNamespace(ra);
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);
        UUID accountId = UUID.randomUUID();
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
        when(activeRegistrarService.resolveForCurrentUser())
                .thenReturn(Optional.of(new Registrar(ra)));

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "OWN-1", "1.0.0", List.of(), List.of());

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getRegistrationAuthority()).isSameAs(ra);
    }

    @Test
    void ensureAdministeredItem_create_externalNamespaceAlsoOwnedByActiveRa() {
        // 外部命名空间（无关联注册机构、非 system-default）：条目仍归属活动身份所在机构
        RegistrationAuthority ra = newRa();
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        ns.setSchemeReference(Sign.of("urn:example:external-standard"));
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        UUID accountId = UUID.randomUUID();
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
        when(activeRegistrarService.resolveForCurrentUser())
                .thenReturn(Optional.of(new Registrar(ra)));

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "EXT-1", "1.0.0", List.of(), List.of());

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getRegistrationAuthority()).isSameAs(ra);
    }

    @Test
    void ensureAdministeredItem_create_rejectsNonRegistrar() {
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        ns.setSchemeReference(Sign.of("urn:example:external-standard"));
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        UUID accountId = UUID.randomUUID();
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
        when(activeRegistrarService.resolveForCurrentUser()).thenReturn(Optional.empty());

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "X-1", "1.0.0", List.of(), List.of());

        assertThatThrownBy(() -> itemAdministrationService.ensureAdministeredItem(item, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Registrar");
    }

    @Test
    void ensureAdministeredItem_create_rejectsCrossAuthorityNamespace() {
        RegistrationAuthority activeRa = newRa();
        RegistrationAuthority otherRa = newRa();
        Namespace ns = raNamespace(otherRa);
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);
        UUID accountId = UUID.randomUUID();
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
        when(activeRegistrarService.resolveForCurrentUser())
                .thenReturn(Optional.of(new Registrar(activeRa)));

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "X-1", "1.0.0", List.of(), List.of());

        assertThatThrownBy(() -> itemAdministrationService.ensureAdministeredItem(item, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("命名空间属于其他注册机构");
    }

    @Test
    void ensureAdministeredItem_create_unauthenticatedKeepsLegacyBehavior() {
        // 种子/系统路径（无登录用户）：不推导归属，维持既有行为
        RegistrationAuthority ra = newRa();
        Namespace ns = raNamespace(ra);
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "X-1", "1.0.0", List.of(), List.of());

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getRegistrationAuthority()).isNull();
    }

    @Test
    void ensureAdministeredItem_update_freezesOwnershipAndAssertsActiveIdentity() {
        RegistrationAuthority ra = newRa();
        Namespace ns = raNamespace(ra);
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);
        AdministeredItem existing = new AdministeredItem(item);
        existing.setRegistrationAuthority(ra);
        item.setIdentifiedItem(existing);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), null, "2.0.0", List.of(), List.of());

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getRegistrationAuthority()).isSameAs(ra); // 归属冻结
        verify(activeRegistrarService).assertCanModify(existing);
    }

    @Test
    void ensureAdministeredItem_explicitAuthority_skipsActiveIdentityAssertion() {
        // 联邦/系统路径显式指定归属（合并时 RAI 匹配的目的方机构）
        RegistrationAuthority targetRa = newRa();
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        ns.setSchemeReference(Sign.of("urn:example:external-standard"));
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "FED-1", "1.0.0", List.of(), List.of());

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req, targetRa);

        assertThat(ai.getRegistrationAuthority()).isSameAs(targetRa);
        verify(activeRegistrarService, never()).assertCanModify(any());
    }

    // ===== default-namespace-by-active-registrar：默认命名空间推导 =====

    @Test
    void ensureAdministeredItem_defaultNamespace_usesActiveRaOwnNamespace() {
        // 未传 namespaceId → 按活动 Registrar 所属 RA 的自有命名空间推导
        RegistrationAuthority ra = newRa();
        Namespace ownNs = raNamespace(ra);
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(UUID.randomUUID());
        when(activeRegistrarService.resolveForCurrentUser())
                .thenReturn(Optional.of(new Registrar(ra)));
        when(namespaceRepository.findFirstByRegistrationAuthority_IdAndSchemeReferenceNot(
                ra.getId(), SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_SCHEME))
                .thenReturn(Optional.of(ownNs));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                null, "AUTO-1", "1.0.0", List.of(), List.of());

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai).isNotNull();
        assertThat(ai.getScopedIdentifiers()).hasSize(1);
        assertThat(ai.getScopedIdentifiers().get(0).getNamespace()).isSameAs(ownNs);
        assertThat(ai.getRegistrationAuthority()).isSameAs(ra);
    }

    @Test
    void ensureAdministeredItem_defaultNamespace_skippedWhenExplicitId() {
        // 显式传入 namespaceId → 默认推导不被调用
        RegistrationAuthority ra = newRa();
        Namespace explicitNs = raNamespace(ra);
        when(namespaceRepository.findById(explicitNs.getId())).thenReturn(Optional.of(explicitNs));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(UUID.randomUUID());
        when(activeRegistrarService.resolveForCurrentUser())
                .thenReturn(Optional.of(new Registrar(ra)));

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                explicitNs.getId(), "EXP-1", "1.0.0", List.of(), List.of());

        itemAdministrationService.ensureAdministeredItem(item, req);

        verify(namespaceRepository, never())
                .findFirstByRegistrationAuthority_IdAndSchemeReferenceNot(any(), any());
    }

    @Test
    void ensureAdministeredItem_defaultNamespace_skippedOnFederationPath() {
        // 联邦/合并（explicitAuthority != null）路径不进行默认推导
        RegistrationAuthority targetRa = newRa();
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                null, "FED-1", "1.0.0", List.of(), List.of());

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req, targetRa);

        assertThat(ai.getRegistrationAuthority()).isSameAs(targetRa);
        verify(namespaceRepository, never())
                .findFirstByRegistrationAuthority_IdAndSchemeReferenceNot(any(), any());
    }

    @Test
    void ensureAdministeredItem_defaultNamespace_unauthenticatedNoDefault() {
        // 未登录（种子/系统路径）→ 不做默认推导；自有数据标准缺少前置命名空间时拒绝
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(false);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                null, "X-1", "1.0.0", List.of(), List.of());

        assertThatThrownBy(() -> itemAdministrationService.ensureAdministeredItem(item, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("命名空间");
        verify(namespaceRepository, never())
                .findFirstByRegistrationAuthority_IdAndSchemeReferenceNot(any(), any());
    }

    @Test
    void ensureAdministeredItem_defaultNamespace_noActiveRegistrarNoDefault() {
        // 当前账号无 Registrar 身份 → 默认推导不出，由 enforceRaNamespacePrecondition 拒绝
        // （spec：明确要求该分支由 enforceRaNamespacePrecondition 接管）
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(UUID.randomUUID());
        when(activeRegistrarService.resolveForCurrentUser()).thenReturn(Optional.empty());
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(false);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                null, "X-1", "1.0.0", List.of(), List.of());

        assertThatThrownBy(() -> itemAdministrationService.ensureAdministeredItem(item, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("命名空间");
    }

    @Test
    void ensureAdministeredItem_defaultNamespace_noActiveRegistrarButRaNamespaceExists_fallsToOwnershipCheck() {
        // 系统中有 RA 命名空间但当前账号无 Registrar → precondition 放行，registration 身份检查报错
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(UUID.randomUUID());
        when(activeRegistrarService.resolveForCurrentUser()).thenReturn(Optional.empty());
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                null, "X-1", "1.0.0", List.of(), List.of());

        assertThatThrownBy(() -> itemAdministrationService.ensureAdministeredItem(item, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Registrar");
    }

    @Test
    void ensureAdministeredItem_defaultNamespace_noOwnNamespaceRejected() {
        // 活动 Registrar 所属 RA 尚未建自有命名空间 → 默认推导返回空，由前置条件拒绝
        RegistrationAuthority ra = newRa();
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(UUID.randomUUID());
        when(activeRegistrarService.resolveForCurrentUser())
                .thenReturn(Optional.of(new Registrar(ra)));
        when(namespaceRepository.findFirstByRegistrationAuthority_IdAndSchemeReferenceNot(
                ra.getId(), SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_SCHEME))
                .thenReturn(Optional.empty());
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(false);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                null, "X-1", "1.0.0", List.of(), List.of());

        assertThatThrownBy(() -> itemAdministrationService.ensureAdministeredItem(item, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("命名空间");
    }

    // ===== registry-merge-strategy：本地编辑版本递增（D4/D5）=====

    private AdministeredItem attachExistingIdentifier(Namespace ns, String identifier, String version) {
        AdministeredItem ai = new AdministeredItem(item);
        ScopedIdentifier si = new ScopedIdentifier(identifier, ns, ai);
        si.setVersion(version);
        ai.getScopedIdentifiers().add(si);
        item.setIdentifiedItem(ai);
        return ai;
    }

    @Test
    void ensureAdministeredItem_update_sameVersionEcho_bumpsVersion() {
        // 请求版本与现行版本相同（前端回显）→ 按命名空间版本策略递增
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        ns.setVersioningPolicy("SEQUENTIAL");
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        AdministeredItem existing = attachExistingIdentifier(ns, "X-1", "1");

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), null, "1", List.of(), List.of());

        itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(existing.getScopedIdentifiers().get(0).getVersion()).isEqualTo("2");
    }

    @Test
    void ensureAdministeredItem_update_nullVersion_bumpsVersion() {
        // 请求未携带版本 → 同样视为本地编辑，版本递增
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        ns.setVersioningPolicy("SEQUENTIAL");
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        AdministeredItem existing = attachExistingIdentifier(ns, "X-1", "3");

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), null, null, List.of(), List.of());

        itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(existing.getScopedIdentifiers().get(0).getVersion()).isEqualTo("4");
    }

    @Test
    void ensureAdministeredItem_update_semverPolicy_bumpsPatch() {
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        ns.setVersioningPolicy("SEMVER");
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        AdministeredItem existing = attachExistingIdentifier(ns, "X-1", "1.0.0");

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), null, null, List.of(), List.of());

        itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(existing.getScopedIdentifiers().get(0).getVersion()).isEqualTo("1.0.1");
    }

    @Test
    void ensureAdministeredItem_update_explicitDifferentVersion_honored() {
        // 请求显式携带不同版本 → 尊重用户指定，不再递增
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        ns.setVersioningPolicy("SEQUENTIAL");
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        AdministeredItem existing = attachExistingIdentifier(ns, "X-1", "1");

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), null, "5.0.0", List.of(), List.of());

        itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(existing.getScopedIdentifiers().get(0).getVersion()).isEqualTo("5.0.0");
    }

    @Test
    void ensureAdministeredItem_update_dualIdentifiers_onlyNamespaceMatchedIdentifierBumped() {
        // 联邦迁移后的双标识条目：本地编辑仅递增目的方命名空间下的标识，
        // 来源命名空间下的标识（合并基）保持不变
        Namespace targetNs = new Namespace();
        targetNs.setId(UUID.randomUUID());
        targetNs.setVersioningPolicy("SEQUENTIAL");
        Namespace sourceCopy = new Namespace();
        sourceCopy.setId(UUID.randomUUID());
        sourceCopy.setSchemeReference(Sign.of("urn:metadata-registry:imported:uuid-1"));
        when(namespaceRepository.findById(targetNs.getId())).thenReturn(Optional.of(targetNs));

        AdministeredItem ai = new AdministeredItem(item);
        ScopedIdentifier targetSi = new ScopedIdentifier("T-1", targetNs, ai);
        targetSi.setVersion("1");
        ScopedIdentifier sourceSi = new ScopedIdentifier("X-1", sourceCopy, ai);
        sourceSi.setVersion("10");
        ai.getScopedIdentifiers().add(sourceSi);
        ai.getScopedIdentifiers().add(targetSi);
        item.setIdentifiedItem(ai);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                targetNs.getId(), null, "1", List.of(), List.of());

        itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(targetSi.getVersion()).isEqualTo("2");
        assertThat(sourceSi.getVersion()).isEqualTo("10");
        assertThat(sourceSi.getNamespace()).isSameAs(sourceCopy);
    }

    @Test
    void ensureAdministeredItem_update_dualIdentifiers_prefersRaOwnIdentifierWhenNamespaceUnmatched() {
        // 请求未定位命名空间时，多标识条目的编辑落在注册机构自有命名空间标识上，
        // 避免触碰来源命名空间标识（合并基）
        RegistrationAuthority ra = newRa();
        Namespace raNs = raNamespace(ra);
        raNs.setVersioningPolicy("SEQUENTIAL");
        Namespace sourceCopy = new Namespace();
        sourceCopy.setId(UUID.randomUUID());
        sourceCopy.setSchemeReference(Sign.of("urn:metadata-registry:imported:uuid-1"));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);

        AdministeredItem ai = new AdministeredItem(item);
        ScopedIdentifier raSi = new ScopedIdentifier("T-1", raNs, ai);
        raSi.setVersion("1");
        ScopedIdentifier sourceSi = new ScopedIdentifier("X-1", sourceCopy, ai);
        sourceSi.setVersion("10");
        ai.getScopedIdentifiers().add(sourceSi);
        ai.getScopedIdentifiers().add(raSi);
        item.setIdentifiedItem(ai);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                null, null, null, List.of(), List.of());

        itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(raSi.getVersion()).isEqualTo("2");
        assertThat(raSi.getNamespace()).isSameAs(raNs);
        assertThat(sourceSi.getVersion()).isEqualTo("10");
    }
}
