package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.StewardshipRecord;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.SubmissionRecord;
import io.github.matrixlane.metadataregistry.ext.basicandcore.ContactAccountBinding;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionInfo;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Spy;
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
    @Mock private ScopedIdentifierLifecycleService lifecycleService;
    @Mock private NamespaceIdentifierSettingsService namespaceIdentifierSettingsService;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.SubmissionRecordRepository submissionRecordRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.StewardshipRecordRepository stewardshipRecordRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.ContactRepository contactRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository organizationRepository;
    @Mock private ContactService contactService;
    @Mock private ContactAccountBindingService contactAccountBindingService;
    @Spy private AdministrativeStatusService administrativeStatusService;

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

        // 卫星实体默认行为:无卫星行时返回默认值(语义同字段时代初始值)
        lenient().when(lifecycleService.getOrDefault(any()))
                .thenAnswer(inv -> new io.github.matrixlane.metadataregistry.ext.identification.ScopedIdentifierLifecycle());
        lenient().when(namespaceIdentifierSettingsService.getOrDefault(any()))
                .thenAnswer(inv -> new io.github.matrixlane.metadataregistry.ext.identification.NamespaceIdentifierSettings());

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
    void applyDesignations_updatesExistingLineById_inPlace() {
        // 携带 id 的 payload 行就地更新：保留行 id（被配对引用时不会触发重建 DELETE）
        Designation existing = new Designation();
        existing.setId(UUID.randomUUID());
        existing.setSign(Sign.of("旧指称"));
        existing.setLanguage(lang);
        existing.setItem(item);
        item.getDesignations().add(existing);

        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        TextLine line = new TextLine(existing.getId(), languageId, "新指称", null, null, null, true);

        itemAdministrationService.applyDesignations(item, List.of(line));

        assertThat(item.getDesignations()).hasSize(1);
        assertThat(item.getDesignations().get(0)).isSameAs(existing);
        assertThat(item.getDesignations().get(0).getId()).isEqualTo(existing.getId());
        assertThat(item.getDesignations().get(0).getSign().getValue()).isEqualTo("新指称");
    }

    @Test
    void applyDesignations_removesUnreferencedExistingLine() {
        Designation existing = new Designation();
        existing.setId(UUID.randomUUID());
        existing.setSign(Sign.of("待删指称"));
        existing.setLanguage(lang);
        existing.setItem(item);
        item.getDesignations().add(existing);

        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        TextLine line = new TextLine(null, languageId, "保留指称", null, null, null, true);

        itemAdministrationService.applyDesignations(item, List.of(line));

        assertThat(item.getDesignations()).hasSize(1);
        assertThat(item.getDesignations().get(0).getSign().getValue()).isEqualTo("保留指称");
    }

    @Test
    void applyDesignations_rejectsRemovalWhenReferencedByPairing() {
        // 既有指称仍被配对引用时禁止随 payload 删除（否则违反配对外键）
        Designation existing = new Designation();
        existing.setId(UUID.randomUUID());
        existing.setSign(Sign.of("被配对引用"));
        existing.setLanguage(lang);
        existing.setItem(item);
        existing.getDesignationDefinitionPairings().add(new io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationDefinitionPairing());
        item.getDesignations().add(existing);

        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        TextLine line = new TextLine(null, languageId, "另一个指称", null, null, null, true);

        assertThatThrownBy(() -> itemAdministrationService.applyDesignations(item, List.of(line)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仍被配对引用");
        assertThat(item.getDesignations()).hasSize(2);
    }

    @Test
    void applyDefinitions_updatesExistingLineById_inPlace() {
        Definition existing = new Definition();
        existing.setId(UUID.randomUUID());
        existing.setText(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text.of("旧定义"));
        existing.setLanguage(lang);
        existing.setItem(item);
        item.getDefinitions().add(existing);

        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        TextLine line = new TextLine(existing.getId(), languageId, "新定义", null, null, null, true);

        itemAdministrationService.applyDefinitions(item, List.of(line));

        assertThat(item.getDefinitions()).hasSize(1);
        assertThat(item.getDefinitions().get(0)).isSameAs(existing);
        assertThat(item.getDefinitions().get(0).getText().getValue()).isEqualTo("新定义");
    }

    @Test
    void applyDefinitions_rejectsRemovalWhenReferencedByPairing() {
        Definition existing = new Definition();
        existing.setId(UUID.randomUUID());
        existing.setText(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text.of("被配对引用"));
        existing.setLanguage(lang);
        existing.setItem(item);
        existing.getDesignationDefinitionPairings().add(new io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationDefinitionPairing());
        item.getDefinitions().add(existing);

        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        TextLine line = new TextLine(null, languageId, "另一个定义", null, null, null, true);

        assertThatThrownBy(() -> itemAdministrationService.applyDefinitions(item, List.of(line)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仍被配对引用");
        assertThat(item.getDefinitions()).hasSize(2);
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

    // ===== 上下文自引用：上下文条目的指称/定义可关联其自身（根上下文创建）=====

    @Test
    void applyDesignations_contextItem_selfReferenceBySentinel_resolvesToSelf() {
        // 新建上下文（尚无 id）以哨兵 NIL UUID 自引用 → 关联到条目自身，无需查库
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));
        Context self = new Context();

        TextLine line = new TextLine(languageId, "根上下文", null,
                List.of(new TextLineContextEntry(
                        ItemAdministrationService.SELF_CONTEXT_REFERENCE, Acceptability.PREFERRED)));

        itemAdministrationService.applyDesignations(self, List.of(line));

        assertThat(self.getDesignations()).hasSize(1);
        assertThat(self.getDesignations().get(0).getDesignationContexts()).hasSize(1);
        assertThat(self.getDesignations().get(0).getDesignationContexts().get(0).getContext())
                .isSameAs(self);
        verifyNoInteractions(contextRepository);
    }

    @Test
    void applyDesignations_contextItem_selfReferenceByOwnId_resolvesToSelf() {
        // 编辑既有上下文：直接引用自身 id 同样成立
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));
        Context self = new Context();
        self.setId(contextId);

        TextLine line = new TextLine(languageId, "根上下文", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.PREFERRED)));

        itemAdministrationService.applyDesignations(self, List.of(line));

        assertThat(self.getDesignations().get(0).getDesignationContexts().get(0).getContext())
                .isSameAs(self);
        verifyNoInteractions(contextRepository);
    }

    @Test
    void applyDesignations_nonContextItem_selfReferenceSentinel_rejected() {
        // 非上下文条目使用自引用哨兵 → 拒绝
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        TextLine line = new TextLine(languageId, "患者姓名", null,
                List.of(new TextLineContextEntry(
                        ItemAdministrationService.SELF_CONTEXT_REFERENCE, Acceptability.PREFERRED)));

        assertThatThrownBy(() -> itemAdministrationService.applyDesignations(item, List.of(line)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅上下文条目可将指称/定义关联到其自身");
    }

    @Test
    void applyDefinitions_contextItem_selfReferenceBySentinel_resolvesToSelf() {
        // 定义同样支持自引用
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));
        Context self = new Context();

        TextLine line = new TextLine(languageId, "根上下文定义", null,
                List.of(new TextLineContextEntry(
                        ItemAdministrationService.SELF_CONTEXT_REFERENCE, Acceptability.PREFERRED)));

        itemAdministrationService.applyDefinitions(self, List.of(line));

        assertThat(self.getDefinitions()).hasSize(1);
        assertThat(self.getDefinitions().get(0).getDefinitionContexts().get(0).getContext())
                .isSameAs(self);
        verifyNoInteractions(contextRepository);
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
        stubVersioningPolicy(ns, "SEQUENTIAL");
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
        stubVersioningPolicy(ns, "SEQUENTIAL");
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
        stubVersioningPolicy(ns, "SEMVER");
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
        stubVersioningPolicy(ns, "SEQUENTIAL");
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
        stubVersioningPolicy(targetNs, "SEQUENTIAL");
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
        stubVersioningPolicy(raNs, "SEQUENTIAL");
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

    private void stubVersioningPolicy(Namespace ns, String policy) {
        var settings = new io.github.matrixlane.metadataregistry.ext.identification.NamespaceIdentifierSettings();
        settings.setNamespaceId(ns.getId());
        settings.setVersioningPolicy(policy);
        lenient().when(namespaceIdentifierSettingsService.getOrDefault(ns.getId())).thenReturn(settings);
    }

    // ===== workspace-administrative-status：编辑后统一置 Draft =====

    @Test
    void ensureAdministeredItem_create_marksDraft() {
        RegistrationAuthority ra = newRa();
        Namespace ns = raNamespace(ra);
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "DRAFT-1", "1.0.0", List.of(), List.of());

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getRegistrationState()).isNotNull();
        assertThat(ai.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.INCOMPLETE);
        assertThat(ai.getRegistrationState().getAdministrativeStatus())
                .isEqualTo(AdministrativeStatus.DRAFT);
        verify(administrativeStatusService).markDraft(ai);
    }

    @Test
    void ensureAdministeredItem_update_recorded_marksDraftAndKeepsRegistrationStatus() {
        RegistrationAuthority ra = newRa();
        Namespace ns = raNamespace(ra);
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);
        AdministeredItem existing = new AdministeredItem(item);
        existing.setRegistrationAuthority(ra);
        RegistrationState state = new RegistrationState();
        state.setRegistrationStatus(RegistrationStatus.RECORDED);
        state.setEffectiveDate(java.time.LocalDateTime.now());
        existing.setRegistrationState(state);
        item.setIdentifiedItem(existing);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), null, "2.0.0", List.of(), List.of());

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.RECORDED);
        assertThat(ai.getRegistrationState().getAdministrativeStatus())
                .isEqualTo(AdministrativeStatus.DRAFT);
        verify(administrativeStatusService).markDraft(ai);
    }

    @Test
    void ensureAdministeredItem_update_rejectedRevision_marksNewDraft() {
        RegistrationAuthority ra = newRa();
        Namespace ns = raNamespace(ra);
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);
        AdministeredItem existing = new AdministeredItem(item);
        existing.setRegistrationAuthority(ra);
        RegistrationState state = new RegistrationState();
        state.setRegistrationStatus(RegistrationStatus.RECORDED);
        state.setAdministrativeStatus(AdministrativeStatus.REJECTED);
        state.setEffectiveDate(java.time.LocalDateTime.now());
        existing.setRegistrationState(state);
        item.setIdentifiedItem(existing);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), null, "2.0.0", List.of(), List.of());

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getRegistrationState().getAdministrativeStatus())
                .isEqualTo(AdministrativeStatus.DRAFT);
        verify(administrativeStatusService).markDraft(ai);
    }

    @Test
    void ensureAdministeredItem_administrativeStatusInRequest_isIgnored() {
        RegistrationAuthority ra = newRa();
        Namespace ns = raNamespace(ra);
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);
        AdministeredItem existing = new AdministeredItem(item);
        existing.setRegistrationAuthority(ra);
        RegistrationState state = new RegistrationState();
        state.setRegistrationStatus(RegistrationStatus.RECORDED);
        state.setEffectiveDate(java.time.LocalDateTime.now());
        existing.setRegistrationState(state);
        item.setIdentifiedItem(existing);

        // 请求体携带自由文本管理状态，应被忽略，最终由 markDraft 置为 DRAFT
        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), null, "2.0.0", List.of(), List.of(), null, null, null,
                null, "approved as recorded", null, null, null, null, null);

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getRegistrationState().getAdministrativeStatus())
                .isEqualTo(AdministrativeStatus.DRAFT);
    }

    @Test
    void ensureAdministeredItem_create_explicitStatus_setsInitialStateDirectly() {
        // 创建路径：仅允许 INCOMPLETE / APPLICATION / HISTORICAL 作为显式初始状态
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "NEW-1", "1.0.0", List.of(), List.of(), null, null, null,
                RegistrationStatus.APPLICATION, null, null, null, null, null, null);

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getRegistrationState().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.APPLICATION);
        assertThat(ai.getRegistrationState().getPreviousState()).isNull();
        assertThat(ai.getRegistrationState().getAdministrativeStatus())
                .isEqualTo(AdministrativeStatus.DRAFT);
    }

    @Test
    void ensureAdministeredItem_savesExplanatoryComment() {
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "CMT-1", "1.0.0", List.of(), List.of(), null, null, null,
                null, null, null, null, null, null, "患者主索引标识的解释");

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getExplanatoryComment()).isEqualTo("患者主索引标识的解释");
    }

    @Test
    void ensureAdministeredItem_explanatoryCommentNull_keepsExistingValue() {
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        AdministeredItem existing = new AdministeredItem(item);
        existing.setExplanatoryComment("既有注释");
        item.setIdentifiedItem(existing);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "CMT-2", "1.0.0", List.of(), List.of(), null, null, null,
                null, null, null, null, null, null, null);

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getExplanatoryComment()).isEqualTo("既有注释");
    }

    @Test
    void ensureAdministeredItem_explanatoryCommentEmptyString_clearsValue() {
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        AdministeredItem existing = new AdministeredItem(item);
        existing.setExplanatoryComment("既有注释");
        item.setIdentifiedItem(existing);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "CMT-3", "1.0.0", List.of(), List.of(), null, null, null,
                null, null, null, null, null, null, "");

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getExplanatoryComment()).isEmpty();
    }

    // ===== stewardship-record：主管记录设置/复用/校验 =====

    private Contact newContact(UUID id, Organization organization) {
        Contact contact = new Contact();
        contact.setId(id);
        contact.setOrganization(organization);
        return contact;
    }

    private Organization newOrganization(UUID id) {
        Organization organization = new Organization();
        organization.setId(id);
        return organization;
    }

    private StewardshipRecord newStewardshipRecord(UUID id, Contact contact, Organization organization) {
        StewardshipRecord record = new StewardshipRecord();
        record.setId(id);
        record.setContact(contact);
        record.setOrganization(organization);
        return record;
    }

    @Test
    void ensureAdministeredItem_withStewardship_createsAndSetsStewardshipRecord() {
        RegistrationAuthority ra = newRa();
        Namespace ns = raNamespace(ra);
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);

        UUID accountId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Organization organization = newOrganization(organizationId);
        Contact contact = newContact(contactId, organization);

        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
        when(activeRegistrarService.resolveForCurrentUser()).thenReturn(Optional.of(new Registrar(ra)));
        when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));
        when(contactAccountBindingService.accountIdOfContact(contactId)).thenReturn(Optional.of(accountId));
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));
        when(stewardshipRecordRepository.findFirstByContact_IdAndOrganization_Id(contactId, organizationId))
                .thenReturn(Optional.empty());
        when(stewardshipRecordRepository.save(any(StewardshipRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "STEW-1", "1.0.0", List.of(), List.of(), null, null, null,
                null, null, null, null, null, null, null, null,
                new io.github.matrixlane.metadataregistry.web.dto.common.StewardshipInfo(contactId, organizationId));

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getStewardshipRecord()).isNotNull();
        assertThat(ai.getStewardshipRecord().getContact()).isSameAs(contact);
        assertThat(ai.getStewardshipRecord().getOrganization()).isSameAs(organization);
        verify(stewardshipRecordRepository).findFirstByContact_IdAndOrganization_Id(contactId, organizationId);
        verify(stewardshipRecordRepository).save(any(StewardshipRecord.class));
    }

    @Test
    void ensureAdministeredItem_withStewardship_reusesExistingRecord() {
        RegistrationAuthority ra = newRa();
        Namespace ns = raNamespace(ra);
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);

        UUID accountId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Organization organization = newOrganization(organizationId);
        Contact contact = newContact(contactId, organization);
        StewardshipRecord existing = newStewardshipRecord(UUID.randomUUID(), contact, organization);

        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
        when(activeRegistrarService.resolveForCurrentUser()).thenReturn(Optional.of(new Registrar(ra)));
        when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));
        when(contactAccountBindingService.accountIdOfContact(contactId)).thenReturn(Optional.of(accountId));
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));
        when(stewardshipRecordRepository.findFirstByContact_IdAndOrganization_Id(contactId, organizationId))
                .thenReturn(Optional.of(existing));

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "STEW-2", "1.0.0", List.of(), List.of(), null, null, null,
                null, null, null, null, null, null, null, null,
                new io.github.matrixlane.metadataregistry.web.dto.common.StewardshipInfo(contactId, organizationId));

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getStewardshipRecord()).isSameAs(existing);
        verify(stewardshipRecordRepository, never()).save(any(StewardshipRecord.class));
    }

    @Test
    void ensureAdministeredItem_withStewardship_usesContactOrganizationWhenOmitted() {
        RegistrationAuthority ra = newRa();
        Namespace ns = raNamespace(ra);
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);

        UUID accountId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Organization organization = newOrganization(organizationId);
        Contact contact = newContact(contactId, organization);

        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
        when(activeRegistrarService.resolveForCurrentUser()).thenReturn(Optional.of(new Registrar(ra)));
        when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));
        when(contactAccountBindingService.accountIdOfContact(contactId)).thenReturn(Optional.of(accountId));
        when(stewardshipRecordRepository.findFirstByContact_IdAndOrganization_Id(contactId, organizationId))
                .thenReturn(Optional.empty());
        when(stewardshipRecordRepository.save(any(StewardshipRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "STEW-3", "1.0.0", List.of(), List.of(), null, null, null,
                null, null, null, null, null, null, null, null,
                new io.github.matrixlane.metadataregistry.web.dto.common.StewardshipInfo(contactId, null));

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getStewardshipRecord()).isNotNull();
        assertThat(ai.getStewardshipRecord().getOrganization()).isSameAs(organization);
    }

    @Test
    void ensureAdministeredItem_stewardshipContactNotBoundToAccount_rejects() {
        RegistrationAuthority ra = newRa();
        Namespace ns = raNamespace(ra);
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));
        when(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).thenReturn(true);

        UUID accountId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        Organization organization = newOrganization(UUID.randomUUID());
        Contact contact = newContact(contactId, organization);

        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
        when(activeRegistrarService.resolveForCurrentUser()).thenReturn(Optional.of(new Registrar(ra)));
        when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));
        when(contactAccountBindingService.accountIdOfContact(contactId)).thenReturn(Optional.empty());

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "STEW-4", "1.0.0", List.of(), List.of(), null, null, null,
                null, null, null, null, null, null, null, null,
                new io.github.matrixlane.metadataregistry.web.dto.common.StewardshipInfo(contactId, organization.getId()));

        assertThatThrownBy(() -> itemAdministrationService.ensureAdministeredItem(item, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("主管信息");
    }

    @Test
    void ensureAdministeredItem_stewardshipNull_keepsExistingRecord() {
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        when(namespaceRepository.findById(ns.getId())).thenReturn(Optional.of(ns));

        StewardshipRecord existing = newStewardshipRecord(UUID.randomUUID(), newContact(UUID.randomUUID(), null), newOrganization(UUID.randomUUID()));
        AdministeredItem administeredItem = new AdministeredItem(item);
        administeredItem.setStewardshipRecord(existing);
        item.setIdentifiedItem(administeredItem);

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), "STEW-5", "1.0.0", List.of(), List.of());

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai.getStewardshipRecord()).isSameAs(existing);
        verifyNoInteractions(stewardshipRecordRepository);
    }

    @Test
    void buildItemMeta_echoesStewardshipRecordIds() {
        UUID aiId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        AdministeredItem administeredItem = new AdministeredItem(item);
        administeredItem.setId(aiId);
        StewardshipRecord record = newStewardshipRecord(recordId, newContact(contactId, null), newOrganization(organizationId));
        administeredItem.setStewardshipRecord(record);

        when(administeredItemRepository.findMetaByItemId(item.getId())).thenReturn(List.<Object[]>of(new Object[]{aiId, RegistrationStatus.INCOMPLETE}));
        when(administeredItemRepository.findByItem_Id(item.getId())).thenReturn(Optional.of(administeredItem));
        when(scopedIdentifierRepository.findByItemId(item.getId())).thenReturn(List.of());

        ItemMetaResponse meta = itemAdministrationService.buildItemMeta(item);

        assertThat(meta.stewardshipRecordId()).isEqualTo(recordId);
        assertThat(meta.stewardshipContactId()).isEqualTo(contactId);
        assertThat(meta.stewardshipOrganizationId()).isEqualTo(organizationId);
    }

    // ===== acg-submission：organizationId 显式给出时的联系人身份解析 =====

    private Item newPersistedItem() {
        Item persisted = new TestItem();
        persisted.setId(UUID.randomUUID());
        AdministeredItem ai = new AdministeredItem(persisted);
        when(administeredItemRepository.findByItem_Id(persisted.getId())).thenReturn(Optional.of(ai));
        return persisted;
    }

    private void stubAccount(UUID accountId) {
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(accountId);
    }

    @Test
    void recordSubmissionForBatch_organizationWithoutIdentity_rejectsWithGuidance() {
        // 提交组织不是当前账号具有联系人身份的组织 → 422 且消息含引导
        UUID accountId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Item persisted = newPersistedItem();
        stubAccount(accountId);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(newOrganization(orgId)));
        when(contactAccountBindingService.bindingsOfAccount(accountId)).thenReturn(List.of());

        SubmissionInfo submission = new SubmissionInfo(null, orgId);

        assertThatThrownBy(() -> itemAdministrationService.recordSubmissionForBatch(
                List.of(persisted), submission))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先创建为该机构联系人");
        verify(submissionRecordRepository, never()).save(any(SubmissionRecord.class));
    }

    @Test
    void recordSubmissionForBatch_contactNotUnderOrganization_rejects() {
        // contactId + organizationId 同时给出：联系人属于当前账号但不属于所选组织 → 422
        UUID accountId = UUID.randomUUID();
        Organization orgA = newOrganization(UUID.randomUUID());
        Organization orgB = newOrganization(UUID.randomUUID());
        Contact identityInA = newContact(UUID.randomUUID(), orgA);
        Contact contactInB = newContact(UUID.randomUUID(), orgB);
        Item persisted = newPersistedItem();
        stubAccount(accountId);
        when(organizationRepository.findById(orgA.getId())).thenReturn(Optional.of(orgA));
        when(contactAccountBindingService.bindingsOfAccount(accountId))
                .thenReturn(List.of(new ContactAccountBinding(identityInA.getId(), accountId)));
        when(contactRepository.findAllById(List.of(identityInA.getId()))).thenReturn(List.of(identityInA));
        when(contactRepository.findById(contactInB.getId())).thenReturn(Optional.of(contactInB));
        when(contactAccountBindingService.accountIdOfContact(contactInB.getId()))
                .thenReturn(Optional.of(accountId));

        SubmissionInfo submission = new SubmissionInfo(contactInB.getId(), orgA.getId());

        assertThatThrownBy(() -> itemAdministrationService.recordSubmissionForBatch(
                List.of(persisted), submission))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须属于所选提交组织");
        verify(submissionRecordRepository, never()).save(any(SubmissionRecord.class));
    }

    @Test
    void recordSubmissionForBatch_contactOnly_keepsLegacyBehavior() {
        // 仅给 contactId（不给 organizationId）：行为与现状一致——联系人须属于当前账号，
        // 组织取联系人所属组织
        UUID accountId = UUID.randomUUID();
        Organization org = newOrganization(UUID.randomUUID());
        Contact contact = newContact(UUID.randomUUID(), org);
        Item persisted = newPersistedItem();
        stubAccount(accountId);
        when(contactRepository.findById(contact.getId())).thenReturn(Optional.of(contact));
        when(contactAccountBindingService.accountIdOfContact(contact.getId()))
                .thenReturn(Optional.of(accountId));
        when(submissionRecordRepository.save(any(SubmissionRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SubmissionInfo submission = new SubmissionInfo(contact.getId(), null);

        itemAdministrationService.recordSubmissionForBatch(
                List.of(persisted), submission);

        ArgumentCaptor<SubmissionRecord> captor =
                ArgumentCaptor.forClass(SubmissionRecord.class);
        verify(submissionRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getContact()).isSameAs(contact);
        assertThat(captor.getValue().getOrganization()).isSameAs(org);
    }

    @Test
    void recordSubmissionForBatch_organizationGiven_resolvesOwnIdentityAsContact() {        // org 给出时 SR.contact 解析为该组织下的本人身份（而非请求中隐含的其它推导）
        UUID accountId = UUID.randomUUID();
        Organization org = newOrganization(UUID.randomUUID());
        Contact identity = newContact(UUID.randomUUID(), org);
        Item persisted = newPersistedItem();
        stubAccount(accountId);
        when(organizationRepository.findById(org.getId())).thenReturn(Optional.of(org));
        when(contactAccountBindingService.bindingsOfAccount(accountId))
                .thenReturn(List.of(new ContactAccountBinding(identity.getId(), accountId)));
        when(contactRepository.findAllById(List.of(identity.getId()))).thenReturn(List.of(identity));
        when(submissionRecordRepository.save(any(SubmissionRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SubmissionInfo submission = new SubmissionInfo(null, org.getId());

        itemAdministrationService.recordSubmissionForBatch(
                List.of(persisted), submission);

        ArgumentCaptor<SubmissionRecord> captor =
                ArgumentCaptor.forClass(SubmissionRecord.class);
        verify(submissionRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getContact()).isSameAs(identity);
        assertThat(captor.getValue().getOrganization()).isSameAs(org);
    }

    @Test
    void recordSubmissionForBatch_multipleIdentitiesInOrganization_defaultBindingWins() {
        // minor-1：同一组织多身份时按"默认绑定优先、其次最早绑定"确定性排序；
        // findAllById 返回顺序未定义——故意逆序返回，验证解析结果不依赖其顺序
        UUID accountId = UUID.randomUUID();
        Organization org = newOrganization(UUID.randomUUID());
        Contact earliest = newContact(UUID.randomUUID(), org);
        Contact defaultIdentity = newContact(UUID.randomUUID(), org);
        Item persisted = newPersistedItem();
        stubAccount(accountId);
        when(organizationRepository.findById(org.getId())).thenReturn(Optional.of(org));
        when(contactAccountBindingService.bindingsOfAccount(accountId)).thenReturn(List.of(
                new ContactAccountBinding(earliest.getId(), accountId),
                new ContactAccountBinding(defaultIdentity.getId(), accountId, true)));
        when(contactRepository.findAllById(any())).thenReturn(List.of(defaultIdentity, earliest));
        when(submissionRecordRepository.save(any(SubmissionRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SubmissionInfo submission = new SubmissionInfo(null, org.getId());

        itemAdministrationService.recordSubmissionForBatch(
                List.of(persisted), submission);

        ArgumentCaptor<SubmissionRecord> captor =
                ArgumentCaptor.forClass(SubmissionRecord.class);
        verify(submissionRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getContact()).isSameAs(defaultIdentity);
    }

    @Test
    void recordSubmissionForBatch_multipleIdentitiesInOrganization_earliestBindingWinsWhenNoDefault() {
        // minor-1：无默认绑定时取最早绑定（contactId 升序首条），与 findAllById 顺序无关
        UUID accountId = UUID.randomUUID();
        Organization org = newOrganization(UUID.randomUUID());
        Contact earliest = newContact(UUID.randomUUID(), org);
        Contact later = newContact(UUID.randomUUID(), org);
        Item persisted = newPersistedItem();
        stubAccount(accountId);
        when(organizationRepository.findById(org.getId())).thenReturn(Optional.of(org));
        when(contactAccountBindingService.bindingsOfAccount(accountId)).thenReturn(List.of(
                new ContactAccountBinding(earliest.getId(), accountId),
                new ContactAccountBinding(later.getId(), accountId)));
        when(contactRepository.findAllById(any())).thenReturn(List.of(later, earliest));
        when(submissionRecordRepository.save(any(SubmissionRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SubmissionInfo submission = new SubmissionInfo(null, org.getId());

        itemAdministrationService.recordSubmissionForBatch(
                List.of(persisted), submission);

        ArgumentCaptor<SubmissionRecord> captor =
                ArgumentCaptor.forClass(SubmissionRecord.class);
        verify(submissionRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getContact()).isSameAs(earliest);
    }
}
