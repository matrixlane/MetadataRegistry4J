package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.itemmapping.entity.MappingScheme;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingSchemeStatus;
import io.github.matrixlane.metadataregistry.itemmapping.repository.MappingSchemeRepository;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingSchemeRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingSchemeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MappingSchemeServiceTest {

    @Mock
    private MappingSchemeRepository repository;

    private MappingSchemeService service;

    private final UUID schemeId = UUID.randomUUID();
    private MappingScheme draftScheme;
    private MappingScheme publishedScheme;
    private MappingScheme archivedScheme;

    @BeforeEach
    void setUp() {
        service = new MappingSchemeService(repository);

        draftScheme = new MappingScheme("Test Scheme", "http://source", "http://target", "description");
        setId(draftScheme, schemeId);
        draftScheme.setCreatedAt(LocalDateTime.of(2026, 5, 13, 0, 0));
        draftScheme.setUpdatedAt(LocalDateTime.of(2026, 5, 13, 0, 0));

        publishedScheme = new MappingScheme("Published", "http://src", "http://dst", null);
        setId(publishedScheme, UUID.randomUUID());
        publishedScheme.setStatus(MappingSchemeStatus.PUBLISHED);
        publishedScheme.setVersion("0.1.0");
        publishedScheme.setCreatedAt(LocalDateTime.of(2026, 5, 12, 0, 0));
        publishedScheme.setUpdatedAt(LocalDateTime.of(2026, 5, 12, 0, 0));

        archivedScheme = new MappingScheme("Archived", "http://a", "http://b", null);
        setId(archivedScheme, UUID.randomUUID());
        archivedScheme.setStatus(MappingSchemeStatus.ARCHIVED);
        archivedScheme.setVersion("0.2.0");
        archivedScheme.setCreatedAt(LocalDateTime.of(2026, 5, 11, 0, 0));
        archivedScheme.setUpdatedAt(LocalDateTime.of(2026, 5, 11, 0, 0));
    }

    // ==================== CRUD Tests ====================

    @Test
    void list_shouldReturnPagedResults() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<MappingScheme> page = new PageImpl<>(List.of(draftScheme, publishedScheme));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<MappingSchemeResponse> result = service.list(null, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).name()).isEqualTo("Test Scheme");
    }

    @Test
    void list_shouldFilterByKeyword() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(draftScheme)));

        Page<MappingSchemeResponse> result = service.list("test", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void get_shouldReturnScheme() {
        when(repository.findById(schemeId)).thenReturn(Optional.of(draftScheme));

        MappingSchemeResponse result = service.get(schemeId);

        assertThat(result.name()).isEqualTo("Test Scheme");
        assertThat(result.version()).isEqualTo("0.1.0");
        assertThat(result.status()).isEqualTo(MappingSchemeStatus.DRAFT);
    }

    @Test
    void get_shouldThrowWhenNotFound() {
        when(repository.findById(schemeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(schemeId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("MappingScheme");
    }

    @Test
    void create_shouldSaveWithInitialValues() {
        MappingSchemeRequest request = new MappingSchemeRequest(
                "New Scheme", "http://source", "http://target", "desc");

        when(repository.save(any())).thenAnswer(invocation -> {
            MappingScheme saved = invocation.getArgument(0);
            setId(saved, UUID.randomUUID());
            return saved;
        });

        MappingSchemeResponse result = service.create(request);

        assertThat(result.name()).isEqualTo("New Scheme");
        assertThat(result.version()).isEqualTo("0.1.0");
        assertThat(result.status()).isEqualTo(MappingSchemeStatus.DRAFT);
        assertThat(result.description()).isEqualTo("desc");

        ArgumentCaptor<MappingScheme> captor = ArgumentCaptor.forClass(MappingScheme.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("New Scheme");
    }

    @Test
    void update_shouldModifyDraftScheme() {
        MappingSchemeRequest request = new MappingSchemeRequest(
                "Updated", "http://new-source", "http://new-target", "new desc");

        when(repository.findById(schemeId)).thenReturn(Optional.of(draftScheme));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MappingSchemeResponse result = service.update(schemeId, request);

        assertThat(result.name()).isEqualTo("Updated");
        assertThat(result.sourceUri()).isEqualTo("http://new-source");
        assertThat(result.targetUri()).isEqualTo("http://new-target");
        assertThat(result.description()).isEqualTo("new desc");
    }

    @Test
    void update_shouldRejectNonDraft() {
        MappingSchemeRequest request = new MappingSchemeRequest(
                "Updated", "http://x", "http://y", null);

        when(repository.findById(publishedScheme.getId())).thenReturn(Optional.of(publishedScheme));

        assertThatThrownBy(() -> service.update(publishedScheme.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能编辑草稿状态的映射方案");
    }

    @Test
    void delete_shouldRemoveDraftScheme() {
        when(repository.findById(schemeId)).thenReturn(Optional.of(draftScheme));

        service.delete(schemeId);

        verify(repository).delete(draftScheme);
    }

    @Test
    void delete_shouldRejectNonDraft() {
        when(repository.findById(publishedScheme.getId())).thenReturn(Optional.of(publishedScheme));

        assertThatThrownBy(() -> service.delete(publishedScheme.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能删除草稿状态的映射方案");
    }

    // ==================== Lifecycle Tests ====================

    @Test
    void transitionStatus_draftToPublished_shouldSucceed() {
        when(repository.findById(schemeId)).thenReturn(Optional.of(draftScheme));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MappingSchemeResponse result = service.transitionStatus(schemeId, MappingSchemeStatus.PUBLISHED);

        assertThat(result.status()).isEqualTo(MappingSchemeStatus.PUBLISHED);
    }

    @Test
    void transitionStatus_publishedToArchived_shouldSucceed() {
        when(repository.findById(publishedScheme.getId())).thenReturn(Optional.of(publishedScheme));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MappingSchemeResponse result = service.transitionStatus(
                publishedScheme.getId(), MappingSchemeStatus.ARCHIVED);

        assertThat(result.status()).isEqualTo(MappingSchemeStatus.ARCHIVED);
    }

    @Test
    void transitionStatus_shouldRejectSameStatus() {
        when(repository.findById(schemeId)).thenReturn(Optional.of(draftScheme));

        assertThatThrownBy(() -> service.transitionStatus(schemeId, MappingSchemeStatus.DRAFT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经是");
    }

    @Test
    void transitionStatus_shouldRejectDraftToArchived() {
        when(repository.findById(schemeId)).thenReturn(Optional.of(draftScheme));

        assertThatThrownBy(() -> service.transitionStatus(schemeId, MappingSchemeStatus.ARCHIVED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的状态迁移");
    }

    @Test
    void transitionStatus_shouldRejectArchivedTransition() {
        when(repository.findById(archivedScheme.getId())).thenReturn(Optional.of(archivedScheme));

        assertThatThrownBy(() -> service.transitionStatus(
                archivedScheme.getId(), MappingSchemeStatus.DRAFT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的状态迁移");
    }

    @Test
    void createNewVersion_shouldCreateDraftWithIncrementedPatch() {
        UUID publishedId = publishedScheme.getId();
        String nextVersion = publishedScheme.nextPatchVersion(); // "0.1.1"

        when(repository.findById(publishedId)).thenReturn(Optional.of(publishedScheme));
        when(repository.existsByParentIdAndVersion(publishedId, nextVersion)).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            MappingScheme saved = inv.getArgument(0);
            setId(saved, UUID.randomUUID());
            return saved;
        });

        MappingSchemeResponse result = service.createNewVersion(publishedId);

        assertThat(result.version()).isEqualTo("0.1.1");
        assertThat(result.status()).isEqualTo(MappingSchemeStatus.DRAFT);
        assertThat(result.parentId()).isEqualTo(publishedId);
        assertThat(result.name()).isEqualTo("Published");
    }

    @Test
    void createNewVersion_shouldRejectNonPublished() {
        when(repository.findById(schemeId)).thenReturn(Optional.of(draftScheme));

        assertThatThrownBy(() -> service.createNewVersion(schemeId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能基于已发布（PUBLISHED）的方案创建新版本");
    }

    @Test
    void createNewVersion_shouldRejectDuplicateVersion() {
        UUID publishedId = publishedScheme.getId();
        String nextVersion = publishedScheme.nextPatchVersion();

        when(repository.findById(publishedId)).thenReturn(Optional.of(publishedScheme));
        when(repository.existsByParentIdAndVersion(publishedId, nextVersion)).thenReturn(true);

        assertThatThrownBy(() -> service.createNewVersion(publishedId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本 0.1.1 已存在");
    }

    // ==================== Domain Logic Tests ====================

    @Test
    void nextPatchVersion_shouldIncrementPatch() {
        MappingScheme scheme = new MappingScheme("test", "s", "t", null);
        scheme.setVersion("0.1.0");
        assertThat(scheme.nextPatchVersion()).isEqualTo("0.1.1");

        scheme.setVersion("1.2.3");
        assertThat(scheme.nextPatchVersion()).isEqualTo("1.2.4");

        scheme.setVersion("10.99.999");
        assertThat(scheme.nextPatchVersion()).isEqualTo("10.99.1000");
    }

    // ==================== Helper ====================

    private static void setId(MappingScheme scheme, UUID id) {
        try {
            var field = MappingScheme.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(scheme, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
