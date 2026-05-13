package io.github.matrixlane.metadataregistry.itemmapping.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FhirMappingConflictWarningTest {

    @Test
    void of_shouldGenerateMessage() {
        UUID itemId = UUID.randomUUID();
        List<String> paths = List.of("Patient.name.given", "Patient.name.family");

        FhirMappingConflictWarning warning = FhirMappingConflictWarning.of(itemId, paths);

        assertThat(warning.localItemId()).isEqualTo(itemId);
        assertThat(warning.conflictingPaths()).isEqualTo(paths);
        assertThat(warning.warningMessage())
                .contains(itemId.toString())
                .contains("2")
                .contains("Patient.name.given")
                .contains("Patient.name.family");
    }

    @Test
    void of_singlePath_shouldStillIncludeCount() {
        UUID itemId = UUID.randomUUID();
        List<String> paths = List.of("Patient.name.given");

        FhirMappingConflictWarning warning = FhirMappingConflictWarning.of(itemId, paths);

        assertThat(warning.warningMessage()).contains("1");
    }
}
