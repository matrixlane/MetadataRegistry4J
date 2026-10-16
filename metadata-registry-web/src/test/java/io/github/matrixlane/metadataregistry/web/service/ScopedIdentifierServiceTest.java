package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.ext.identification.IdentifierVersionHistory;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.ext.identification.VersionStatus;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierCreateRequest;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierResponse;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.VersionTransitionRequest;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierVersionHistoryRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegisteredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.service.identifier.IdentifierInputPolicyService;
import io.github.matrixlane.metadataregistry.web.service.namespace.NamespaceConstraintValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import io.github.matrixlane.metadataregistry.ext.identification.ScopedIdentifierLifecycle;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScopedIdentifierServiceTest {

    @Mock
    private ScopedIdentifierRepository scopedIdentifierRepository;
    @Mock
    private IdentifierVersionHistoryRepository historyRepository;
    @Mock
    private NamespaceRepository namespaceRepository;
    @Mock
    private RegisteredItemRepository registeredItemRepository;
    @Mock
    private IdentifierInputPolicyService identifierInputPolicyService;

    @Mock
    private NamespaceConstraintValidator namespaceConstraintValidator;
    @Mock
    private ScopedIdentifierLifecycleService lifecycleService;

    @InjectMocks
    private ScopedIdentifierService service;

    private UUID namespaceId;
    private UUID identifiedItemId;
    private UUID scopedIdentifierId;
    private Namespace namespace;
    private AdministeredItem identifiedItem;

    @BeforeEach
    void setUp() {
        namespaceId = UUID.randomUUID();
        identifiedItemId = UUID.randomUUID();
        scopedIdentifierId = UUID.randomUUID();

        namespace = new Namespace();
        namespace.setId(namespaceId);

        identifiedItem = new AdministeredItem();
        identifiedItem.setCreationDateTime(LocalDateTime.now());
        identifiedItem.setId(identifiedItemId);

        // 卫星实体默认行为：无卫星行时返回默认值(语义同字段时代初始值)
        lenient().when(lifecycleService.getOrDefault(any()))
                .thenAnswer(inv -> new ScopedIdentifierLifecycle());
    }

    @Test
    void list_shouldReturnPage() {
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0");
        Page<ScopedIdentifier> page = new PageImpl<>(List.of(si));
        when(scopedIdentifierRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Page<ScopedIdentifierResponse> result = service.list(PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).identifier()).isEqualTo("id-1");
    }

    @Test
    void get_shouldReturnScopedIdentifier() {
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0");
        when(scopedIdentifierRepository.findById(scopedIdentifierId)).thenReturn(Optional.of(si));

        ScopedIdentifierResponse result = service.get(scopedIdentifierId);

        assertThat(result.identifier()).isEqualTo("id-1");
    }

    @Test
    void get_shouldThrowWhenNotFound() {
        when(scopedIdentifierRepository.findById(scopedIdentifierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(scopedIdentifierId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_shouldGenerateIdentifierWhenBlank() {
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(registeredItemRepository.existsById(identifiedItemId)).thenReturn(true);
        when(registeredItemRepository.findItemIdById(identifiedItemId)).thenReturn(Optional.empty());
        when(registeredItemRepository.getReferenceById(identifiedItemId)).thenReturn(identifiedItem);
        when(identifierInputPolicyService.resolveIdentifier(eq(namespace), isNull(), anyString(), any()))
                .thenReturn("gen-id-123");
        when(identifierInputPolicyService.resolveVersion(eq(namespace), isNull())).thenReturn("1.0.0");
        when(scopedIdentifierRepository.existsByNamespaceIdAndIdentifier(namespaceId, "gen-id-123")).thenReturn(false);
        when(scopedIdentifierRepository.save(any(ScopedIdentifier.class))).thenAnswer(i -> {
            ScopedIdentifier si = i.getArgument(0);
            si.setId(UUID.randomUUID());
            return si;
        });
        when(historyRepository.save(any(IdentifierVersionHistory.class))).thenAnswer(i -> i.getArgument(0));

        ScopedIdentifierCreateRequest request = new ScopedIdentifierCreateRequest(
                namespaceId, null, null, identifiedItemId, null);

        ScopedIdentifierResponse result = service.create(request);

        assertThat(result.identifier()).isEqualTo("gen-id-123");
        assertThat(result.version()).isEqualTo("1.0.0");
        assertThat(result.versionStatus()).isEqualTo(VersionStatus.DRAFT);
    }

    @Test
    void create_shouldUseProvidedIdentifier() {
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(registeredItemRepository.existsById(identifiedItemId)).thenReturn(true);
        when(registeredItemRepository.findItemIdById(identifiedItemId)).thenReturn(Optional.empty());
        when(registeredItemRepository.getReferenceById(identifiedItemId)).thenReturn(identifiedItem);
        when(identifierInputPolicyService.resolveIdentifier(eq(namespace), eq("custom-id"), anyString(), any()))
                .thenReturn("custom-id");
        when(identifierInputPolicyService.resolveVersion(namespace, "2.0.0")).thenReturn("2.0.0");
        when(scopedIdentifierRepository.existsByNamespaceIdAndIdentifier(namespaceId, "custom-id")).thenReturn(false);
        when(scopedIdentifierRepository.save(any(ScopedIdentifier.class))).thenAnswer(i -> {
            ScopedIdentifier si = i.getArgument(0);
            si.setId(UUID.randomUUID());
            return si;
        });
        when(historyRepository.save(any(IdentifierVersionHistory.class))).thenAnswer(i -> i.getArgument(0));

        ScopedIdentifierCreateRequest request = new ScopedIdentifierCreateRequest(
                namespaceId, "custom-id", "2.0.0", identifiedItemId, "Initial");

        ScopedIdentifierResponse result = service.create(request);

        assertThat(result.identifier()).isEqualTo("custom-id");
        assertThat(result.version()).isEqualTo("2.0.0");
    }

    @Test
    void create_shouldThrowWhenDuplicateIdentifier() {
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(registeredItemRepository.existsById(identifiedItemId)).thenReturn(true);
        when(registeredItemRepository.findItemIdById(identifiedItemId)).thenReturn(Optional.empty());
        when(registeredItemRepository.getReferenceById(identifiedItemId)).thenReturn(identifiedItem);
        when(identifierInputPolicyService.resolveIdentifier(eq(namespace), eq("dup-id"), anyString(), any()))
                .thenReturn("dup-id");
        when(scopedIdentifierRepository.existsByNamespaceIdAndIdentifier(namespaceId, "dup-id")).thenReturn(true);

        ScopedIdentifierCreateRequest request = new ScopedIdentifierCreateRequest(
                namespaceId, "dup-id", "1.0.0", identifiedItemId, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void create_shouldHandleConcurrentConflict() {
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(registeredItemRepository.existsById(identifiedItemId)).thenReturn(true);
        when(registeredItemRepository.findItemIdById(identifiedItemId)).thenReturn(Optional.empty());
        when(registeredItemRepository.getReferenceById(identifiedItemId)).thenReturn(identifiedItem);
        when(scopedIdentifierRepository.existsByNamespaceIdAndIdentifier(any(), any())).thenReturn(false);
        when(scopedIdentifierRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        ScopedIdentifierCreateRequest request = new ScopedIdentifierCreateRequest(
                namespaceId, "id", "1.0.0", identifiedItemId, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("concurrent conflict");
    }

    @Test
    void create_shouldThrowWhenNamespaceNotFound() {
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.empty());

        ScopedIdentifierCreateRequest request = new ScopedIdentifierCreateRequest(
                namespaceId, "id", "1.0.0", identifiedItemId, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_shouldThrowWhenIdentifiedItemNotFound() {
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(registeredItemRepository.existsById(identifiedItemId)).thenReturn(false);

        ScopedIdentifierCreateRequest request = new ScopedIdentifierCreateRequest(
                namespaceId, "id", "1.0.0", identifiedItemId, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_shouldThrowForInvalidVersion() {
        when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(registeredItemRepository.existsById(identifiedItemId)).thenReturn(true);
        when(registeredItemRepository.findItemIdById(identifiedItemId)).thenReturn(Optional.empty());
        when(registeredItemRepository.getReferenceById(identifiedItemId)).thenReturn(identifiedItem);
        when(identifierInputPolicyService.resolveIdentifier(eq(namespace), eq("id"), anyString(), any()))
                .thenReturn("id");
        when(identifierInputPolicyService.resolveVersion(namespace, "invalid"))
                .thenThrow(new BusinessException(ApiCodes.VALIDATION_FAILED, "invalid version"));

        ScopedIdentifierCreateRequest request = new ScopedIdentifierCreateRequest(
                namespaceId, "id", "invalid", identifiedItemId, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_shouldUpdateVersion() {
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0");
        si.setNamespace(namespace);
        si.setIdentifiedItem(identifiedItem);
        when(scopedIdentifierRepository.findById(scopedIdentifierId)).thenReturn(Optional.of(si));
        when(scopedIdentifierRepository.save(si)).thenReturn(si);

        ScopedIdentifierCreateRequest request = new ScopedIdentifierCreateRequest(
                namespaceId, "id-1", "2.0.0", identifiedItemId, null);

        ScopedIdentifierResponse result = service.update(scopedIdentifierId, request);

        assertThat(result.version()).isEqualTo("2.0.0");
    }

    @Test
    void update_nullMode_allowsIdentifierChange() {
        // identifierCreationMode 为 NULL（兼容既存记录）→ 等价 MANUAL，应允许 identifier 修改。
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0");
        si.setNamespace(namespace);
        si.setIdentifiedItem(identifiedItem);
        when(scopedIdentifierRepository.findById(scopedIdentifierId)).thenReturn(Optional.of(si));
        when(scopedIdentifierRepository.save(si)).thenReturn(si);

        ScopedIdentifierCreateRequest request = new ScopedIdentifierCreateRequest(
                namespaceId, "new-id", "1.0.0", identifiedItemId, null);

        ScopedIdentifierResponse result = service.update(scopedIdentifierId, request);

        assertThat(result.identifier()).isEqualTo("new-id");
    }

    @Test
    void update_autoMode_rejectsIdentifierChange() {
        // identifierCreationMode 为 AUTO → 后端 MUST 拒绝 identifier 修改。
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0");
        si.setNamespace(namespace);
        si.setIdentifiedItem(identifiedItem);
        ScopedIdentifierLifecycle lc = new ScopedIdentifierLifecycle();
        lc.setIdentifierCreationMode(io.github.matrixlane.metadataregistry.ext.identification.IdentifierCreationMode.AUTO);
        when(lifecycleService.getOrDefault(scopedIdentifierId)).thenReturn(lc);
        when(scopedIdentifierRepository.findById(scopedIdentifierId)).thenReturn(Optional.of(si));

        ScopedIdentifierCreateRequest request = new ScopedIdentifierCreateRequest(
                namespaceId, "new-id", "1.0.0", identifiedItemId, null);

        assertThatThrownBy(() -> service.update(scopedIdentifierId, request))
                .isInstanceOf(io.github.matrixlane.metadataregistry.web.common.BusinessException.class)
                .hasMessageContaining("自动生成，不允许手动修改");
    }

    @Test
    void transition_shouldPublishFromDraft() {
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0");
        si.setNamespace(namespace);
        si.setIdentifiedItem(identifiedItem);
        ScopedIdentifierLifecycle lc = new ScopedIdentifierLifecycle();
        lc.setVersionStatus(VersionStatus.DRAFT);
        when(lifecycleService.getOrDefault(scopedIdentifierId)).thenReturn(lc);
        lenient().when(lifecycleService.mutate(eq(scopedIdentifierId), any())).thenAnswer(inv -> {
            java.util.function.Consumer<ScopedIdentifierLifecycle> c = inv.getArgument(1);
            c.accept(lc);
            return lc;
        });
        when(scopedIdentifierRepository.findById(scopedIdentifierId)).thenReturn(Optional.of(si));
        when(scopedIdentifierRepository.save(si)).thenReturn(si);
        when(historyRepository.save(any(IdentifierVersionHistory.class))).thenAnswer(i -> i.getArgument(0));

        VersionTransitionRequest request = new VersionTransitionRequest(VersionStatus.PUBLISHED, "Ready");
        ScopedIdentifierResponse result = service.transition(scopedIdentifierId, request);

        assertThat(result.versionStatus()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(lc.getPublishedAt()).isNotNull();
    }

    @Test
    void transition_shouldDeprecateFromPublished() {
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0");
        si.setNamespace(namespace);
        si.setIdentifiedItem(identifiedItem);
        ScopedIdentifierLifecycle lc = new ScopedIdentifierLifecycle();
        lc.setVersionStatus(VersionStatus.PUBLISHED);
        when(lifecycleService.getOrDefault(scopedIdentifierId)).thenReturn(lc);
        lenient().when(lifecycleService.mutate(eq(scopedIdentifierId), any())).thenAnswer(inv -> {
            java.util.function.Consumer<ScopedIdentifierLifecycle> c = inv.getArgument(1);
            c.accept(lc);
            return lc;
        });
        when(scopedIdentifierRepository.findById(scopedIdentifierId)).thenReturn(Optional.of(si));
        when(scopedIdentifierRepository.save(si)).thenReturn(si);
        when(historyRepository.save(any(IdentifierVersionHistory.class))).thenAnswer(i -> i.getArgument(0));

        VersionTransitionRequest request = new VersionTransitionRequest(VersionStatus.DEPRECATED, "Old");
        ScopedIdentifierResponse result = service.transition(scopedIdentifierId, request);

        assertThat(result.versionStatus()).isEqualTo(VersionStatus.DEPRECATED);
        assertThat(lc.getDeprecatedAt()).isNotNull();
    }

    @Test
    void transition_shouldObsoleteFromDeprecated() {
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0");
        si.setNamespace(namespace);
        si.setIdentifiedItem(identifiedItem);
        ScopedIdentifierLifecycle lc = new ScopedIdentifierLifecycle();
        lc.setVersionStatus(VersionStatus.DEPRECATED);
        when(lifecycleService.getOrDefault(scopedIdentifierId)).thenReturn(lc);
        lenient().when(lifecycleService.mutate(eq(scopedIdentifierId), any())).thenAnswer(inv -> {
            java.util.function.Consumer<ScopedIdentifierLifecycle> c = inv.getArgument(1);
            c.accept(lc);
            return lc;
        });
        when(scopedIdentifierRepository.findById(scopedIdentifierId)).thenReturn(Optional.of(si));
        when(scopedIdentifierRepository.save(si)).thenReturn(si);
        when(historyRepository.save(any(IdentifierVersionHistory.class))).thenAnswer(i -> i.getArgument(0));

        VersionTransitionRequest request = new VersionTransitionRequest(VersionStatus.OBSOLETE, "Gone");
        ScopedIdentifierResponse result = service.transition(scopedIdentifierId, request);

        assertThat(result.versionStatus()).isEqualTo(VersionStatus.OBSOLETE);
        assertThat(lc.getObsoleteAt()).isNotNull();
    }

    @Test
    void transition_shouldRejectInvalidTransition() {
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0");
        si.setNamespace(namespace);
        si.setIdentifiedItem(identifiedItem);
        ScopedIdentifierLifecycle lc = new ScopedIdentifierLifecycle();
        lc.setVersionStatus(VersionStatus.OBSOLETE);
        when(lifecycleService.getOrDefault(scopedIdentifierId)).thenReturn(lc);
        lenient().when(lifecycleService.mutate(eq(scopedIdentifierId), any())).thenAnswer(inv -> {
            java.util.function.Consumer<ScopedIdentifierLifecycle> c = inv.getArgument(1);
            c.accept(lc);
            return lc;
        });
        when(scopedIdentifierRepository.findById(scopedIdentifierId)).thenReturn(Optional.of(si));

        VersionTransitionRequest request = new VersionTransitionRequest(VersionStatus.PUBLISHED, "No");

        assertThatThrownBy(() -> service.transition(scopedIdentifierId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid version transition");
    }

    @Test
    void delete_shouldRemove() {
        when(scopedIdentifierRepository.existsById(scopedIdentifierId)).thenReturn(true);
        service.delete(scopedIdentifierId);
        verify(scopedIdentifierRepository).deleteById(scopedIdentifierId);
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        when(scopedIdentifierRepository.existsById(scopedIdentifierId)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(scopedIdentifierId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listHistory_shouldReturnRecords() {
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0");
        si.setId(scopedIdentifierId);
        IdentifierVersionHistory h = new IdentifierVersionHistory(
                si, "1.0.0", VersionStatus.DRAFT, "system", "Created");
        when(historyRepository.findByScopedIdentifierIdOrderByTimestampDesc(scopedIdentifierId))
                .thenReturn(List.of(h));

        var result = service.listHistory(scopedIdentifierId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).version()).isEqualTo("1.0.0");
    }

    private ScopedIdentifier createScopedIdentifier(String identifier, String version) {
        ScopedIdentifier si = new ScopedIdentifier();
        si.setId(scopedIdentifierId);
        si.setIdentifier(identifier);
        si.setVersion(version);
        return si;
    }
}
