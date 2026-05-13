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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FhirMappingConflictDetectorTest {

    @Mock
    private FhirElementMappingRepository repository;

    private FhirMappingConflictDetector detector;

    @BeforeEach
    void setUp() {
        detector = new FhirMappingConflictDetector(repository);
    }

    // ---- Single item conflict detection ----

    @Test
    void detectConflicts_noMappings_shouldReturnEmpty() {
        UUID itemId = UUID.randomUUID();
        when(repository.findByLocalItemId(itemId)).thenReturn(Collections.emptyList());

        List<FhirMappingConflictWarning> result = detector.detectConflicts(itemId);

        assertThat(result).isEmpty();
    }

    @Test
    void detectConflicts_singleMapping_shouldReturnEmpty() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT
        );
        when(repository.findByLocalItemId(itemId)).thenReturn(List.of(mapping));

        List<FhirMappingConflictWarning> result = detector.detectConflicts(itemId);

        assertThat(result).isEmpty();
    }

    @Test
    void detectConflicts_multipleDifferentPaths_shouldReturnWarning() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping m1 = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT);
        FhirElementMapping m2 = new FhirElementMapping(
                itemId, "Patient.name.family", "Patient", FhirMappingEquivalence.EQUIVALENT);
        when(repository.findByLocalItemId(itemId)).thenReturn(List.of(m1, m2));

        List<FhirMappingConflictWarning> result = detector.detectConflicts(itemId);

        assertThat(result).hasSize(1);
        FhirMappingConflictWarning warning = result.get(0);
        assertThat(warning.localItemId()).isEqualTo(itemId);
        assertThat(warning.conflictingPaths()).containsExactlyInAnyOrder(
                "Patient.name.given", "Patient.name.family");
        assertThat(warning.warningMessage()).contains(itemId.toString());
    }

    @Test
    void detectConflicts_samePathMultipleTimes_shouldNotReturnWarning() {
        UUID itemId = UUID.randomUUID();
        // Two mappings to the same path (unlikely in practice but testable)
        FhirElementMapping m1 = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT);
        FhirElementMapping m2 = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUAL);
        when(repository.findByLocalItemId(itemId)).thenReturn(List.of(m1, m2));

        List<FhirMappingConflictWarning> result = detector.detectConflicts(itemId);

        assertThat(result).isEmpty();
    }

    // ---- Bulk conflict detection ----

    @Test
    void detectAllConflicts_noConflicts_shouldReturnEmpty() {
        when(repository.findLocalItemIdsWithMultipleMappings()).thenReturn(Collections.emptyList());

        List<FhirMappingConflictWarning> result = detector.detectAllConflicts();

        assertThat(result).isEmpty();
    }

    @Test
    void detectAllConflicts_withConflicts_shouldReturnWarnings() {
        UUID item1 = UUID.randomUUID();
        UUID item2 = UUID.randomUUID();

        when(repository.findLocalItemIdsWithMultipleMappings()).thenReturn(List.of(item1, item2));

        FhirElementMapping m1 = new FhirElementMapping(
                item1, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT);
        FhirElementMapping m2 = new FhirElementMapping(
                item1, "Patient.name.family", "Patient", FhirMappingEquivalence.EQUIVALENT);
        FhirElementMapping m3 = new FhirElementMapping(
                item2, "Observation.valueString", "Observation", FhirMappingEquivalence.EQUIVALENT);
        FhirElementMapping m4 = new FhirElementMapping(
                item2, "Observation.valueQuantity.value", "Observation", FhirMappingEquivalence.RELATEDTO);

        when(repository.findByLocalItemIds(List.of(item1, item2)))
                .thenReturn(List.of(m1, m2, m3, m4));

        List<FhirMappingConflictWarning> result = detector.detectAllConflicts();

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(FhirMappingConflictWarning::localItemId)
                .containsExactlyInAnyOrder(item1, item2);
    }

    @Test
    void detectAllConflicts_shouldQueryRepositoryCorrectly() {
        when(repository.findLocalItemIdsWithMultipleMappings()).thenReturn(Collections.emptyList());

        detector.detectAllConflicts();

        verify(repository).findLocalItemIdsWithMultipleMappings();
    }

    // ---- Scheme-scoped conflict detection ----

    @Test
    void detectConflictsByScheme_noMappings_shouldReturnEmpty() {
        UUID schemeId = UUID.randomUUID();
        when(repository.findByMappingSchemeId(schemeId)).thenReturn(Collections.emptyList());

        List<FhirMappingConflictWarning> result = detector.detectConflictsByScheme(schemeId);

        assertThat(result).isEmpty();
    }

    @Test
    void detectConflictsByScheme_withConflicts_shouldReturnWarnings() {
        UUID schemeId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        FhirElementMapping m1 = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient",
                FhirMappingEquivalence.EQUIVALENT,
                FhirMappingType.ONE_TO_ONE, null, null, schemeId);
        FhirElementMapping m2 = new FhirElementMapping(
                itemId, "Patient.name.family", "Patient",
                FhirMappingEquivalence.EQUIVALENT,
                FhirMappingType.ONE_TO_ONE, null, null, schemeId);

        when(repository.findByMappingSchemeId(schemeId)).thenReturn(List.of(m1, m2));

        List<FhirMappingConflictWarning> result = detector.detectConflictsByScheme(schemeId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).conflictingPaths())
                .containsExactlyInAnyOrder("Patient.name.given", "Patient.name.family");
    }

    // ---- Performance / scale test ----

    @Test
    void detectConflicts_largeDataset_shouldCompleteQuickly() {
        UUID itemId = UUID.randomUUID();
        List<FhirElementMapping> mappings = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            mappings.add(new FhirElementMapping(
                    itemId, "Patient.element" + i, "Patient", FhirMappingEquivalence.EQUIVALENT));
        }
        when(repository.findByLocalItemId(itemId)).thenReturn(mappings);

        long start = System.currentTimeMillis();
        List<FhirMappingConflictWarning> result = detector.detectConflicts(itemId);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result).hasSize(1);
        assertThat(result.get(0).conflictingPaths()).hasSize(1000);
        assertThat(elapsed).isLessThan(100); // ≤ 100ms requirement
    }

    // ---- analyzeMappings edge cases ----

    @Test
    void analyzeMappings_nullList_shouldReturnEmpty() {
        List<FhirMappingConflictWarning> result =
                detector.analyzeMappings(UUID.randomUUID(), null);
        assertThat(result).isEmpty();
    }

    @Test
    void analyzeMappings_emptyList_shouldReturnEmpty() {
        List<FhirMappingConflictWarning> result =
                detector.analyzeMappings(UUID.randomUUID(), Collections.emptyList());
        assertThat(result).isEmpty();
    }

    @Test
    void analyzeMappings_singleMapping_shouldReturnEmpty() {
        UUID itemId = UUID.randomUUID();
        List<FhirMappingConflictWarning> result =
                detector.analyzeMappings(itemId, List.of(
                        new FhirElementMapping(itemId, "Patient.name", "Patient",
                                FhirMappingEquivalence.EQUIVALENT)));
        assertThat(result).isEmpty();
    }
}
