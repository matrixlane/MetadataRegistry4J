package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.VersionStatus;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierResponse;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentifierResolutionServiceTest {

    @Mock
    private ScopedIdentifierRepository scopedIdentifierRepository;

    @InjectMocks
    private IdentifierResolutionService service;

    private UUID namespaceId;
    private UUID identifiedItemId;

    @BeforeEach
    void setUp() {
        namespaceId = UUID.randomUUID();
        identifiedItemId = UUID.randomUUID();
    }

    @Test
    void resolve_shouldFindByNamespaceAndIdentifierAndVersion() {
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0", VersionStatus.PUBLISHED);
        when(scopedIdentifierRepository.findByNamespaceIdAndIdentifierAndVersion(
                namespaceId, "id-1", "1.0.0")).thenReturn(Optional.of(si));

        Optional<ScopedIdentifierResponse> result = service.resolve(namespaceId, "id-1", "1.0.0");

        assertThat(result).isPresent();
        assertThat(result.get().identifier()).isEqualTo("id-1");
    }

    @Test
    void resolve_shouldFindByNamespaceAndIdentifierWithoutVersion() {
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0", VersionStatus.PUBLISHED);
        when(scopedIdentifierRepository.findByNamespaceIdAndIdentifier(
                namespaceId, "id-1")).thenReturn(Optional.of(si));

        Optional<ScopedIdentifierResponse> result = service.resolve(namespaceId, "id-1", null);

        assertThat(result).isPresent();
    }

    @Test
    void resolve_shouldReturnEmptyWhenNotFound() {
        when(scopedIdentifierRepository.findByNamespaceIdAndIdentifierAndVersion(
                namespaceId, "id-1", "1.0.0")).thenReturn(Optional.empty());

        Optional<ScopedIdentifierResponse> result = service.resolve(namespaceId, "id-1", "1.0.0");

        assertThat(result).isEmpty();
    }

    @Test
    void resolveGlobally_shouldFindLatestNonObsolete() {
        ScopedIdentifier si1 = createScopedIdentifier("id-1", "1.0.0", VersionStatus.OBSOLETE);
        si1.setCreatedAt(LocalDateTime.now().minusDays(2));
        ScopedIdentifier si2 = createScopedIdentifier("id-1", "2.0.0", VersionStatus.PUBLISHED);
        si2.setCreatedAt(LocalDateTime.now().minusDays(1));
        when(scopedIdentifierRepository.findByIdentifier("id-1")).thenReturn(List.of(si1, si2));

        Optional<ScopedIdentifierResponse> result = service.resolveGlobally("id-1", null);

        assertThat(result).isPresent();
        assertThat(result.get().version()).isEqualTo("2.0.0");
    }

    @Test
    void resolveGlobally_shouldFindByVersion() {
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0", VersionStatus.PUBLISHED);
        when(scopedIdentifierRepository.findByIdentifier("id-1")).thenReturn(List.of(si));

        Optional<ScopedIdentifierResponse> result = service.resolveGlobally("id-1", "1.0.0");

        assertThat(result).isPresent();
    }

    @Test
    void resolveGlobally_shouldReturnEmptyWhenNoMatch() {
        when(scopedIdentifierRepository.findByIdentifier("id-1")).thenReturn(List.of());

        Optional<ScopedIdentifierResponse> result = service.resolveGlobally("id-1", null);

        assertThat(result).isEmpty();
    }

    @Test
    void resolveGlobally_shouldFallbackToFirstWhenAllObsolete() {
        ScopedIdentifier si = createScopedIdentifier("id-1", "1.0.0", VersionStatus.OBSOLETE);
        when(scopedIdentifierRepository.findByIdentifier("id-1")).thenReturn(List.of(si));

        Optional<ScopedIdentifierResponse> result = service.resolveGlobally("id-1", null);

        assertThat(result).isPresent();
        assertThat(result.get().version()).isEqualTo("1.0.0");
    }

    @Test
    void findByIdentifiedItem_shouldReturnAllIdentifiers() {
        ScopedIdentifier si1 = createScopedIdentifier("id-1", "1.0.0", VersionStatus.PUBLISHED);
        ScopedIdentifier si2 = createScopedIdentifier("id-2", "1.0.0", VersionStatus.DRAFT);
        when(scopedIdentifierRepository.findByIdentifiedItemId(identifiedItemId))
                .thenReturn(List.of(si1, si2));

        List<ScopedIdentifierResponse> result = service.findByIdentifiedItem(identifiedItemId);

        assertThat(result).hasSize(2);
    }

    @Test
    void findByIdentifiedItem_shouldReturnEmptyList() {
        when(scopedIdentifierRepository.findByIdentifiedItemId(identifiedItemId))
                .thenReturn(List.of());

        List<ScopedIdentifierResponse> result = service.findByIdentifiedItem(identifiedItemId);

        assertThat(result).isEmpty();
    }

    private ScopedIdentifier createScopedIdentifier(String identifier, String version, VersionStatus status) {
        ScopedIdentifier si = new ScopedIdentifier();
        si.setId(UUID.randomUUID());
        si.setIdentifier(identifier);
        si.setVersion(version);
        si.setVersionStatus(status);
        si.setNamespace(new Namespace());
        si.getNamespace().setId(namespaceId);
        si.setIdentifiedItem(new IdentifiedItem());
        si.getIdentifiedItem().setId(identifiedItemId);
        si.setCreatedAt(LocalDateTime.now());
        return si;
    }
}
