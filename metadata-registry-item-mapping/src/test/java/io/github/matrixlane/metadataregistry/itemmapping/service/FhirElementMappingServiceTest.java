package io.github.matrixlane.metadataregistry.itemmapping.service;

import io.github.matrixlane.metadataregistry.itemmapping.dto.FhirMappingConflictWarning;
import io.github.matrixlane.metadataregistry.itemmapping.entity.FhirElementMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingEquivalence;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.FhirElementMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FhirElementMappingServiceTest {

    @Mock
    private FhirElementMappingRepository repository;

    @Mock
    private FhirMappingConflictDetector conflictDetector;

    private FhirElementMappingService service;

    @BeforeEach
    void setUp() {
        service = new FhirElementMappingService(repository, conflictDetector);
    }

    // ---- Create: one-to-one ----

    @Test
    void createOneToOneMapping_shouldSaveMapping() {
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FhirElementMapping saved = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT
        );
        saved.setId(UUID.randomUUID());

        when(repository.existsByLocalItemIdAndFhirPath(itemId, "Patient.name.given"))
                .thenReturn(false);
        when(repository.save(any(FhirElementMapping.class))).thenReturn(saved);

        FhirElementMapping result = service.createOneToOneMapping(
                itemId, "Patient.name.given", "Patient",
                FhirMappingEquivalence.EQUIVALENT, "Desc", userId);

        assertThat(result.getId()).isEqualTo(saved.getId());
        assertThat(result.getMappingType()).isEqualTo(FhirMappingType.ONE_TO_ONE);
        verify(repository).save(any(FhirElementMapping.class));
    }

    @Test
    void createOneToOneMapping_duplicate_shouldThrow() {
        UUID itemId = UUID.randomUUID();
        when(repository.existsByLocalItemIdAndFhirPath(itemId, "Patient.name.given"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createOneToOneMapping(
                itemId, "Patient.name.given", "Patient",
                FhirMappingEquivalence.EQUIVALENT, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate mapping");
    }

    // ---- Create: one-to-many ----

    @Test
    void createOneToManyMapping_shouldSaveMapping() {
        UUID itemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FhirElementMapping saved = new FhirElementMapping(
                itemId, "Patient.name.family", "Patient",
                FhirMappingEquivalence.EQUIVALENT,
                FhirMappingType.ONE_TO_MANY, "family", "Family name"
        );
        saved.setId(UUID.randomUUID());

        when(repository.existsByLocalItemIdAndFhirPath(itemId, "Patient.name.family"))
                .thenReturn(false);
        when(repository.save(any(FhirElementMapping.class))).thenReturn(saved);

        FhirElementMapping result = service.createOneToManyMapping(
                itemId, "Patient.name.family", "Patient",
                FhirMappingEquivalence.EQUIVALENT, "family",
                "Family name", userId);

        assertThat(result.getMappingType()).isEqualTo(FhirMappingType.ONE_TO_MANY);
        assertThat(result.getMappingVariable()).isEqualTo("family");
    }

    @Test
    void createOneToManyMapping_blankVariable_shouldThrow() {
        UUID itemId = UUID.randomUUID();
        assertThatThrownBy(() -> service.createOneToManyMapping(
                itemId, "Patient.name.family", "Patient",
                FhirMappingEquivalence.EQUIVALENT, "  ",
                null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mappingVariable");
    }

    @Test
    void createOneToManyMapping_nullVariable_shouldThrow() {
        UUID itemId = UUID.randomUUID();
        assertThatThrownBy(() -> service.createOneToManyMapping(
                itemId, "Patient.name.family", "Patient",
                FhirMappingEquivalence.EQUIVALENT, null,
                null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mappingVariable");
    }

    // ---- Read ----

    @Test
    void findByLocalItemId_shouldReturnMappings() {
        UUID itemId = UUID.randomUUID();
        when(repository.findByLocalItemId(itemId)).thenReturn(List.of());

        List<FhirElementMapping> result = service.findByLocalItemId(itemId);

        assertThat(result).isEmpty();
        verify(repository).findByLocalItemId(itemId);
    }

    @Test
    void findByResourceType_shouldReturnMappings() {
        when(repository.findByFhirResourceType("Patient")).thenReturn(List.of());

        List<FhirElementMapping> result = service.findByResourceType("Patient");

        assertThat(result).isEmpty();
        verify(repository).findByFhirResourceType("Patient");
    }

    @Test
    void findAll_shouldDelegateToRepository() {
        when(repository.findAll()).thenReturn(List.of());
        assertThat(service.findAll()).isEmpty();
        verify(repository).findAll();
    }

    @Test
    void findById_shouldReturnMapping() {
        UUID id = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                UUID.randomUUID(), "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT
        );
        when(repository.findById(id)).thenReturn(Optional.of(mapping));

        Optional<FhirElementMapping> result = service.findById(id);

        assertThat(result).isPresent();
    }

    // ---- Update ----

    @Test
    void updateMapping_shouldUpdateAndSave() {
        UUID id = UUID.randomUUID();
        UUID updater = UUID.randomUUID();
        FhirElementMapping existing = new FhirElementMapping(
                UUID.randomUUID(), "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT
        );
        existing.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(FhirElementMapping.class))).thenReturn(existing);

        FhirElementMapping result = service.updateMapping(
                id, "Patient.name.family", FhirMappingEquivalence.WIDER,
                FhirMappingType.ONE_TO_MANY, "family", "Updated", updater);

        assertThat(result.getFhirPath()).isEqualTo("Patient.name.family");
        assertThat(result.getFhirEquivalence()).isEqualTo(FhirMappingEquivalence.WIDER);
        verify(repository).save(existing);
    }

    @Test
    void updateMapping_notFound_shouldThrow() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMapping(
                id, null, null, null, null, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void updateMapping_changingPathDuplicate_shouldThrow() {
        UUID id = UUID.randomUUID();
        UUID localItemId = UUID.randomUUID();
        FhirElementMapping existing = new FhirElementMapping(
                localItemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT
        );
        existing.setId(id);

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.existsByLocalItemIdAndFhirPath(localItemId, "Patient.name.family"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.updateMapping(
                id, "Patient.name.family", null, null, null, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate mapping");
    }

    // ---- Delete ----

    @Test
    void deleteMapping_shouldDeleteWhenExists() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);

        service.deleteMapping(id);
        verify(repository).deleteById(id);
    }

    @Test
    void deleteMapping_notFound_shouldThrow() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteMapping(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deleteByLocalItemId_shouldDeleteAllForItem() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping m1 = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT);
        FhirElementMapping m2 = new FhirElementMapping(
                itemId, "Patient.name.family", "Patient", FhirMappingEquivalence.EQUIVALENT);
        when(repository.findByLocalItemId(itemId)).thenReturn(List.of(m1, m2));

        long count = service.deleteByLocalItemId(itemId);

        assertThat(count).isEqualTo(2);
        verify(repository).deleteAll(List.of(m1, m2));
    }

    // ---- Conflict Detection Delegation ----

    @Test
    void detectConflicts_shouldDelegateToDetector() {
        UUID itemId = UUID.randomUUID();
        List<FhirMappingConflictWarning> warnings = List.of();
        when(conflictDetector.detectConflicts(itemId)).thenReturn(warnings);

        List<FhirMappingConflictWarning> result = service.detectConflicts(itemId);

        assertThat(result).isSameAs(warnings);
        verify(conflictDetector).detectConflicts(itemId);
    }

    @Test
    void detectAllConflicts_shouldDelegateToDetector() {
        List<FhirMappingConflictWarning> warnings = List.of();
        when(conflictDetector.detectAllConflicts()).thenReturn(warnings);

        List<FhirMappingConflictWarning> result = service.detectAllConflicts();

        assertThat(result).isSameAs(warnings);
        verify(conflictDetector).detectAllConflicts();
    }
}
