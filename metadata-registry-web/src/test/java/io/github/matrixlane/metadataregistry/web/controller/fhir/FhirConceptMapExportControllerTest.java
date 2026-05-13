package io.github.matrixlane.metadataregistry.web.controller.fhir;

import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirConceptMapExportResult;
import io.github.matrixlane.metadataregistry.web.service.fhir.FhirConceptMapExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link FhirConceptMapExportController}.
 *
 * <p>Covers endpoint routing: sync export, async export, task status query,
 * error handling, and response content type verification.
 */
@ExtendWith(MockitoExtension.class)
class FhirConceptMapExportControllerTest {

    @Mock
    private FhirConceptMapExportService exportService;

    private FhirConceptMapExportController controller;

    @BeforeEach
    void setUp() {
        controller = new FhirConceptMapExportController(exportService);
    }

    // ========================================================================
    // POST /api/v1/mappings/export/fhir — Export ConceptMap
    // ========================================================================

    @Test
    void exportConceptMap_sync_returnsFhirJson() {
        String schemeId = "test-scheme";
        FhirConceptMapExportResult syncResult = FhirConceptMapExportResult.syncSuccess(
                "{\"resourceType\":\"ConceptMap\",\"id\":\"test\"}",
                true, java.util.List.of(), 5, 5);

        when(exportService.exportConceptMap(eq(schemeId), any(UUID.class)))
                .thenReturn(syncResult);

        ResponseEntity<?> response = controller.exportConceptMap(schemeId, null);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("conceptmap-test-scheme.json");
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/fhir+json");
        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat((String) response.getBody()).contains("ConceptMap");
    }

    @Test
    void exportConceptMap_async_returns202() {
        String schemeId = "all";
        UUID taskId = UUID.randomUUID();
        FhirConceptMapExportResult asyncResult = FhirConceptMapExportResult.asyncSubmitted(taskId);

        when(exportService.exportConceptMap(eq(schemeId), any(UUID.class)))
                .thenReturn(asyncResult);

        ResponseEntity<?> response = controller.exportConceptMap(schemeId, null);

        assertThat(response.getStatusCodeValue()).isEqualTo(202);
    }

    @Test
    void exportConceptMap_notFound_returns404() {
        String schemeId = UUID.randomUUID().toString();
        FhirConceptMapExportResult notFound = FhirConceptMapExportResult.failure(
                "No mappings found for schemeId: " + schemeId);

        when(exportService.exportConceptMap(eq(schemeId), any(UUID.class)))
                .thenReturn(notFound);

        ResponseEntity<?> response = controller.exportConceptMap(schemeId, null);

        assertThat(response.getStatusCodeValue()).isEqualTo(404);
    }

    @Test
    void exportConceptMap_failure_returns400() {
        String schemeId = "fail-scheme";
        FhirConceptMapExportResult failure = FhirConceptMapExportResult.failure(
                "Export failed: database error");

        when(exportService.exportConceptMap(eq(schemeId), any(UUID.class)))
                .thenReturn(failure);

        ResponseEntity<?> response = controller.exportConceptMap(schemeId, null);

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
    }

    @Test
    void exportConceptMap_emptySchemeId_returns400() {
        ResponseEntity<?> response = controller.exportConceptMap("", null);

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
    }

    @Test
    void exportConceptMap_nullSchemeId_returns400() {
        ResponseEntity<?> response = controller.exportConceptMap(null, null);

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
    }

    @Test
    void exportConceptMap_blankSchemeId_returns400() {
        ResponseEntity<?> response = controller.exportConceptMap("  ", null);

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
    }

    // ========================================================================
    // GET /api/v1/mappings/export/fhir/tasks/{taskId} — Task status
    // ========================================================================

    @Test
    void getExportTaskStatus_found_returnsOk() {
        UUID taskId = UUID.randomUUID();
        FhirConceptMapExportResult completed = FhirConceptMapExportResult.syncSuccess(
                "{\"resourceType\":\"ConceptMap\"}", true,
                java.util.List.of(), 10, 10);

        when(exportService.getExportTaskStatus(taskId)).thenReturn(completed);

        var response = controller.getExportTaskStatus(taskId);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody().getCode()).isZero();
        assertThat(response.getBody().getData().success()).isTrue();
    }

    @Test
    void getExportTaskStatus_notFound_returns404() {
        UUID taskId = UUID.randomUUID();
        FhirConceptMapExportResult notFound = FhirConceptMapExportResult.failure(
                "Export task not found: " + taskId);

        when(exportService.getExportTaskStatus(taskId)).thenReturn(notFound);

        var response = controller.getExportTaskStatus(taskId);

        assertThat(response.getStatusCodeValue()).isEqualTo(404);
    }
}
