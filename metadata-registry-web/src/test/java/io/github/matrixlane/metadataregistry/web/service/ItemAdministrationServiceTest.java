package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierStrategyConfigService;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineContextEntry;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.web.entity.DefinitionAuditLog;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamingConventionRepository;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionEngine;
import io.github.matrixlane.metadataregistry.web.service.namespace.NamespaceConstraintValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock private DefinitionAuditLogService definitionAuditLogService;
    @Mock private IdentifierStrategyConfigService identifierStrategyConfigService;
    @Mock private NamingConventionRepository namingConventionRepository;
    @Mock private NamespaceConstraintValidator namespaceConstraintValidator;

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

        TextLine line = new TextLine(languageId, "", null, null);

        assertThatThrownBy(() -> itemAdministrationService.applyDesignations(item, List.of(line)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("命名规范校验失败");
    }

    @Test
    void applyDesignations_shouldRejectDuplicatePreferred() {
        when(namingConventionEngine.validate(any())).thenReturn(List.of());

        TextLine line1 = new TextLine(languageId, "首选名称1", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.PREFERRED)));
        TextLine line2 = new TextLine(languageId, "首选名称2", null,
                List.of(new TextLineContextEntry(contextId, Acceptability.PREFERRED)));

        assertThatThrownBy(() -> itemAdministrationService.applyDesignations(item, List.of(line1, line2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能有一个首选名称");
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

        TextLine line = new TextLine(languageId, "患者姓名", null, null, List.of(ncId));

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
        TextLine line = new TextLine(languageId, "患者姓名", null, null, null);
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        itemAdministrationService.applyDesignations(item, List.of(line), namespaceId);

        verify(namespaceConstraintValidator).validateDesignation(eq(namespaceId), any(Designation.class), eq(null));
    }

    @Test
    void applyDefinitions_shouldSucceedAndLogAudit() {
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        TextLine line = new TextLine(languageId, "患者的唯一标识", null, null);

        itemAdministrationService.applyDefinitions(item, List.of(line));

        assertThat(item.getDefinitions()).hasSize(1);
        assertThat(item.getDefinitions().get(0).getText().getValue()).isEqualTo("患者的唯一标识");

        ArgumentCaptor<DefinitionAuditLog> logCaptor = ArgumentCaptor.forClass(DefinitionAuditLog.class);
        verify(definitionAuditLogService).save(logCaptor.capture());
        DefinitionAuditLog log = logCaptor.getValue();
        assertThat(log.getAction()).isEqualTo("CREATE");
        assertThat(log.getItemId()).isEqualTo(itemId);
    }

    @Test
    void applyDefinitions_shouldLogUpdateWhenReplacingExisting() {
        Definition existing = new Definition();
        existing.setId(UUID.randomUUID());
        existing.setText(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text.of("旧定义"));
        existing.setLanguage(lang);
        existing.setItem(item);
        item.getDefinitions().add(existing);

        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        TextLine line = new TextLine(languageId, "新定义", null, null);

        itemAdministrationService.applyDefinitions(item, List.of(line));

        ArgumentCaptor<DefinitionAuditLog> logCaptor = ArgumentCaptor.forClass(DefinitionAuditLog.class);
        verify(definitionAuditLogService).save(logCaptor.capture());
        DefinitionAuditLog log = logCaptor.getValue();
        assertThat(log.getAction()).isEqualTo("UPDATE");
        assertThat(log.getOldValueSummary()).isEqualTo("旧定义");
        assertThat(log.getNewValueSummary()).isEqualTo("新定义");
    }

    @Test
    void ensureAdministeredItem_shouldUseManualIdentifier() {
        UUID namespaceId = UUID.randomUUID();
        Namespace ns = new Namespace();
        ns.setId(namespaceId);
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(ns));

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                namespaceId, "MANUAL-123", "1.0.0", null, List.of(), List.of()
        );

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai).isNotNull();
        assertThat(ai.getScopedIdentifiers()).hasSize(1);
        assertThat(ai.getScopedIdentifiers().get(0).getIdentifier()).isEqualTo("MANUAL-123");
        verifyNoInteractions(identifierStrategyConfigService);
    }

    @Test
    void ensureAdministeredItem_shouldAutoGenerateIdentifierWhenEmpty() {
        UUID namespaceId = UUID.randomUUID();
        Namespace ns = new Namespace();
        ns.setId(namespaceId);
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(ns));
        when(identifierStrategyConfigService.generateIdentifier(eq(ns), any()))
                .thenReturn("AUTO-999");

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                namespaceId, "", "1.0.0", null, List.of(), List.of()
        );

        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item, req);

        assertThat(ai).isNotNull();
        assertThat(ai.getScopedIdentifiers()).hasSize(1);
        assertThat(ai.getScopedIdentifiers().get(0).getIdentifier()).isEqualTo("AUTO-999");
        verify(identifierStrategyConfigService).generateIdentifier(eq(ns), any());
    }

    @jakarta.persistence.Entity
    static class TestItem extends Item {}
}
