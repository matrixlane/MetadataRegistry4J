package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationRequest;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationResponse;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamingConventionRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConflictDetector;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionEngine;
import io.github.matrixlane.metadataregistry.web.service.namespace.NamespaceConstraintValidator;
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
class DesignationServiceTest {

    @Mock private DesignationRepository designationRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private LanguageIdentificationRepository languageRepository;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private ContextRepository contextRepository;
    @Mock private ReferenceDocumentRepository referenceDocumentRepository;
    @Mock private NamingConventionEngine namingConventionEngine;
    @Mock private NamingConflictDetector namingConflictDetector;
    @Mock private NamespaceConstraintValidator namespaceConstraintValidator;
    @Mock private NamingConventionRepository namingConventionRepository;
    @Mock private RegistrySpecificationRepository registrySpecificationRepository;

    @InjectMocks
    private DesignationService designationService;

    private UUID itemId;
    private UUID languageId;
    private UUID designationId;
    private Item item;
    private LanguageIdentification lang;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        languageId = UUID.randomUUID();
        designationId = UUID.randomUUID();

        item = new TestItem();
        item.setId(itemId);

        lang = new LanguageIdentification();
        lang.setId(languageId);
        lang.setLanguageIdentifier("zh-CN");
        lenient().when(registrySpecificationRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void listByItemId_shouldReturnResults() {
        Designation d = createDesignation("患者姓名");
        when(designationRepository.findByItem_Id(itemId)).thenReturn(List.of(d));

        List<DesignationResponse> result = designationService.listByItemId(itemId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).value()).isEqualTo("患者姓名");
    }

    @Test
    void get_shouldReturnDesignation() {
        Designation d = createDesignation("患者姓名");
        when(designationRepository.findById(designationId)).thenReturn(Optional.of(d));

        DesignationResponse result = designationService.get(designationId);
        assertThat(result.value()).isEqualTo("患者姓名");
    }

    @Test
    void get_shouldThrowWhenNotFound() {
        when(designationRepository.findById(designationId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> designationService.get(designationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_shouldSucceed() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));
        when(designationRepository.save(any(Designation.class))).thenAnswer(i -> {
            Designation d = i.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DesignationRequest request = new DesignationRequest(
                itemId, languageId, "患者姓名", null, null, null, null);

        DesignationResponse result = designationService.create(request);
        assertThat(result.value()).isEqualTo("患者姓名");
    }

    @Test
    void create_shouldThrowWhenNamingConventionFails() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(namingConventionEngine.validate(any())).thenReturn(List.of(
                io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionRuleResult.fail(
                        "length", "名称不能为空")
        ));
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        DesignationRequest request = new DesignationRequest(
                itemId, languageId, "", null, null, null, null);

        assertThatThrownBy(() -> designationService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("命名规范校验失败");
    }

    @Test
    void create_shouldThrowWhenNamingConflictDetected() {
        UUID namespaceId = UUID.randomUUID();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(namingConflictDetector.hasConflict(eq(namespaceId), any(), any())).thenReturn(true);
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        DesignationRequest request = new DesignationRequest(
                itemId, languageId, "患者姓名", null, namespaceId, null, null);

        assertThatThrownBy(() -> designationService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("命名冲突");
    }

    @Test
    void create_shouldThrowWhenDuplicatePreferred() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(designationRepository.findPreferredByItemIdAndLanguageId(itemId, languageId))
                .thenReturn(List.of(createDesignation("现有首选")));
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        DesignationRequest request = new DesignationRequest(
                itemId, languageId, "新名称", null, null,
                null,
                List.of(new DesignationRequest.ContextEntry(
                        UUID.randomUUID(), Acceptability.PREFERRED)));

        assertThatThrownBy(() -> designationService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能有一个 PREFERRED");
    }

    @Test
    void create_withContexts_shouldSucceed() {
        UUID contextId = UUID.randomUUID();
        var ctx = new io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context();
        ctx.setId(contextId);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(ctx));
        when(designationRepository.save(any(Designation.class))).thenAnswer(i -> {
            Designation d = i.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DesignationRequest request = new DesignationRequest(
                itemId, languageId, "患者姓名", null, null,
                null,
                List.of(new DesignationRequest.ContextEntry(contextId, Acceptability.PREFERRED)));

        DesignationResponse result = designationService.create(request);
        assertThat(result.value()).isEqualTo("患者姓名");
        assertThat(result.contexts()).hasSize(1);
        assertThat(result.contexts().get(0).acceptability()).isEqualTo("PREFERRED");
    }

    @Test
    void create_shouldThrowWhenDuplicatePreferredInSameRequest() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(languageRepository.findById(languageId)).thenReturn(Optional.of(lang));

        UUID ctxId1 = UUID.randomUUID();
        UUID ctxId2 = UUID.randomUUID();

        DesignationRequest request = new DesignationRequest(
                itemId, languageId, "患者姓名", null, null,
                null,
                List.of(
                        new DesignationRequest.ContextEntry(ctxId1, Acceptability.PREFERRED),
                        new DesignationRequest.ContextEntry(ctxId2, Acceptability.PREFERRED)));

        assertThatThrownBy(() -> designationService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能有一个 PREFERRED");
    }

    @Test
    void delete_shouldRemove() {
        when(designationRepository.existsById(designationId)).thenReturn(true);
        designationService.delete(designationId);
        verify(designationRepository).deleteById(designationId);
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        when(designationRepository.existsById(designationId)).thenReturn(false);
        assertThatThrownBy(() -> designationService.delete(designationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_shouldReturnContexts() {
        UUID contextId = UUID.randomUUID();
        Designation d = createDesignation("患者姓名");
        var ctx = new io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context();
        ctx.setId(contextId);
        var dc = new io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationContext();
        dc.setDesignation(d);
        dc.setContext(ctx);
        dc.setAcceptability(Acceptability.ADMITTED);
        d.getDesignationContexts().add(dc);

        when(designationRepository.findById(designationId)).thenReturn(Optional.of(d));

        DesignationResponse result = designationService.get(designationId);
        assertThat(result.contexts()).hasSize(1);
        assertThat(result.contexts().get(0).contextId()).isEqualTo(contextId);
        assertThat(result.contexts().get(0).acceptability()).isEqualTo("ADMITTED");
    }

    private Designation createDesignation(String value) {
        Designation d = new Designation();
        d.setId(designationId);
        d.setSign(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign.of(value));
        d.setLanguage(lang);
        d.setItem(item);
        return d;
    }

    @Test
    void create_shouldResolveDefaultLanguageWhenLanguageIdOmitted() {
        when(registrySpecificationRepository.findAll()).thenReturn(List.of(defaultRegistrySpecification()));
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(namingConventionEngine.validate(any())).thenReturn(List.of());
        when(designationRepository.save(any(Designation.class))).thenAnswer(i -> {
            Designation d = i.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DesignationRequest request = new DesignationRequest(
                itemId, null, "默认语言指称", null, null, null, null);

        DesignationResponse result = designationService.create(request);
        assertThat(result.value()).isEqualTo("默认语言指称");
        assertThat(result.languageId()).isEqualTo(languageId);
    }

    @Test
    void create_shouldRejectWhenNoDefaultLanguageConfigured() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        DesignationRequest request = new DesignationRequest(
                itemId, null, "无默认语言指称", null, null, null, null);

        assertThatThrownBy(() -> designationService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置注册表默认语言");
    }

    private io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification defaultRegistrySpecification() {
        io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification rs =
                new io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification();
        rs.setId(UUID.randomUUID());
        rs.setDefaultLanguage(lang);
        return rs;
    }
    @jakarta.persistence.Entity
    static class TestItem extends Item {}
}
