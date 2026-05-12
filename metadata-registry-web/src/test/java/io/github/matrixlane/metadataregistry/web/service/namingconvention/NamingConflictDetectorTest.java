package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamingConflictDetectorTest {

    @Mock
    private DesignationRepository designationRepository;

    private NamingConflictDetector detector;
    private UUID namespaceId;

    @BeforeEach
    void setUp() {
        detector = new NamingConflictDetector(designationRepository);
        namespaceId = UUID.randomUUID();
    }

    @Test
    void hasConflict_shouldReturnTrueWhenConflictExists() {
        when(designationRepository.existsByNamespaceIdAndSignValueAndItemIdNot(
                eq(namespaceId), eq("patientName"), any()))
                .thenReturn(true);

        boolean result = detector.hasConflict(namespaceId, Sign.of("patientName"));
        assertThat(result).isTrue();
    }

    @Test
    void hasConflict_shouldReturnFalseWhenNoConflict() {
        when(designationRepository.existsByNamespaceIdAndSignValueAndItemIdNot(
                eq(namespaceId), eq("patientName"), any()))
                .thenReturn(false);

        boolean result = detector.hasConflict(namespaceId, Sign.of("patientName"));
        assertThat(result).isFalse();
    }

    @Test
    void hasConflict_shouldReturnFalseForNullNamespace() {
        boolean result = detector.hasConflict(null, Sign.of("patientName"));
        assertThat(result).isFalse();
    }

    @Test
    void hasConflict_shouldReturnFalseForNullSign() {
        boolean result = detector.hasConflict(namespaceId, null);
        assertThat(result).isFalse();
    }

    @Test
    void hasConflict_shouldReturnFalseForBlankSign() {
        boolean result = detector.hasConflict(namespaceId, Sign.of(""));
        assertThat(result).isFalse();
    }

    @Test
    void hasConflict_withExcludeItemId_shouldPassToRepository() {
        UUID excludeItemId = UUID.randomUUID();
        when(designationRepository.existsByNamespaceIdAndSignValueAndItemIdNot(
                namespaceId, "patientName", excludeItemId))
                .thenReturn(true);

        boolean result = detector.hasConflict(namespaceId, Sign.of("patientName"), excludeItemId);
        assertThat(result).isTrue();
    }
}
