package io.github.matrixlane.metadataregistry.web.controller.fhir;

import io.github.matrixlane.metadataregistry.itemmapping.dto.FhirMappingConflictWarning;
import io.github.matrixlane.metadataregistry.itemmapping.entity.FhirElementMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingEquivalence;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingType;
import io.github.matrixlane.metadataregistry.itemmapping.service.FhirElementMappingService;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirConceptMapExportResult;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirElementMappingRequest;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirElementMappingResponse;
import io.github.matrixlane.metadataregistry.web.service.fhir.FhirElementMappingExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link FhirElementMappingController}.
 *
 * <p>Covers CRUD, conflict detection, and ConceptMap export endpoints.
 */
@ExtendWith(MockitoExtension.class)
class FhirElementMappingControllerTest {

    @Mock
    private FhirElementMappingService mappingService;

    @Mock
    private FhirElementMappingExportService exportService;

    private FhirElementMappingController controller;

    @BeforeEach
    void setUp() {
        controller = new FhirElementMappingController(mappingService, exportService);
    }

    // ==================== Read ====================

    @Test
    void list_all_shouldReturnMappings() {
        FhirElementMapping m = sampleMapping();
        when(mappingService.findAll()).thenReturn(List.of(m));

        ApiResponse<List<FhirElementMappingResponse>> response = controller.list(null, null, null);

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).fhirPath()).isEqualTo("Patient.name.given");
    }

    @Test
    void list_byLocalItemId_shouldFilter() {
        UUID itemId = UUID.randomUUID();
        when(mappingService.findByLocalItemId(itemId)).thenReturn(List.of());

        ApiResponse<List<FhirElementMappingResponse>> response = controller.list(itemId, null, null);

        assertThat(response.getData()).isEmpty();
        verify(mappingService).findByLocalItemId(itemId);
    }

    @Test
    void list_byResourceType_shouldFilter() {
        when(mappingService.findByResourceType("Patient")).thenReturn(List.of());

        ApiResponse<List<FhirElementMappingResponse>> response = controller.list(null, "Patient", null);

        assertThat(response.getData()).isEmpty();
        verify(mappingService).findByResourceType("Patient");
    }

    @Test
    void list_bySchemeId_shouldFilter() {
        UUID schemeId = UUID.randomUUID();
        when(mappingService.findBySchemeId(schemeId)).thenReturn(List.of());

        ApiResponse<List<FhirElementMappingResponse>> response = controller.list(null, null, schemeId);

        assertThat(response.getData()).isEmpty();
        verify(mappingService).findBySchemeId(schemeId);
    }

    @Test
    void get_existing_shouldReturnMapping() {
        UUID id = UUID.randomUUID();
        FhirElementMapping m = sampleMapping();
        when(mappingService.findById(id)).thenReturn(Optional.of(m));

        ApiResponse<FhirElementMappingResponse> response = controller.get(id);

        assertThat(response.getData().fhirPath()).isEqualTo("Patient.name.given");
    }

    @Test
    void get_notFound_shouldThrow() {
        UUID id = UUID.randomUUID();
        when(mappingService.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mapping not found");
    }

    // ==================== Create ====================

    @Test
    void create_oneToOne_shouldReturnCreatedMapping() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping saved = sampleMapping();
        FhirElementMappingRequest request = new FhirElementMappingRequest(
                itemId, "Patient.name.given", "Patient",
                FhirMappingEquivalence.EQUIVALENT, FhirMappingType.ONE_TO_ONE,
                null, "Given name", null);

        when(mappingService.createOneToOneMapping(
                eq(itemId), eq("Patient.name.given"), eq("Patient"),
                eq(FhirMappingEquivalence.EQUIVALENT), eq("Given name"),
                eq(null), any(UUID.class)))
                .thenReturn(saved);

        ApiResponse<FhirElementMappingResponse> response = controller.create(request, null);

        assertThat(response.getData().fhirPath()).isEqualTo("Patient.name.given");
    }

    @Test
    void create_oneToMany_shouldReturnCreatedMapping() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping saved = new FhirElementMapping(
                itemId, "Patient.name.family", "Patient",
                FhirMappingEquivalence.EQUIVALENT,
                FhirMappingType.ONE_TO_MANY, "family", "Family name", null);
        saved.setId(UUID.randomUUID());

        FhirElementMappingRequest request = new FhirElementMappingRequest(
                itemId, "Patient.name.family", "Patient",
                FhirMappingEquivalence.EQUIVALENT, FhirMappingType.ONE_TO_MANY,
                "family", "Family name", null);

        when(mappingService.createOneToManyMapping(
                eq(itemId), eq("Patient.name.family"), eq("Patient"),
                eq(FhirMappingEquivalence.EQUIVALENT), eq("family"),
                eq("Family name"), eq(null), any(UUID.class)))
                .thenReturn(saved);

        ApiResponse<FhirElementMappingResponse> response = controller.create(request, null);

        assertThat(response.getData().mappingVariable()).isEqualTo("family");
        assertThat(response.getData().mappingType()).isEqualTo(FhirMappingType.ONE_TO_MANY);
    }

    @Test
    void create_withPrincipal_shouldResolveUserId() {
        UUID userId = UUID.randomUUID();
        Principal principal = () -> userId.toString();
        FhirElementMapping saved = sampleMapping();
        FhirElementMappingRequest request = new FhirElementMappingRequest(
                UUID.randomUUID(), "Patient.name.given", "Patient",
                FhirMappingEquivalence.EQUIVALENT, FhirMappingType.ONE_TO_ONE,
                null, null, null);

        when(mappingService.createOneToOneMapping(
                any(), any(), any(), any(), any(), any(), eq(userId)))
                .thenReturn(saved);

        controller.create(request, principal);

        verify(mappingService).createOneToOneMapping(
                any(), any(), any(), any(), any(), any(), eq(userId));
    }

    // ==================== Update ====================

    @Test
    void update_shouldReturnUpdatedMapping() {
        UUID id = UUID.randomUUID();
        FhirElementMapping updated = sampleMapping();
        FhirElementMappingRequest request = new FhirElementMappingRequest(
                UUID.randomUUID(), "Patient.name.family", "Patient",
                FhirMappingEquivalence.WIDER, FhirMappingType.ONE_TO_ONE,
                null, "Updated", null);

        when(mappingService.updateMapping(
                eq(id), eq("Patient.name.family"), eq(FhirMappingEquivalence.WIDER),
                eq(FhirMappingType.ONE_TO_ONE), eq(null), eq("Updated"), any(UUID.class)))
                .thenReturn(updated);

        ApiResponse<FhirElementMappingResponse> response = controller.update(id, request, null);

        assertThat(response.getData().fhirPath()).isEqualTo("Patient.name.given");
    }

    // ==================== Delete ====================

    @Test
    void delete_shouldCallService() {
        UUID id = UUID.randomUUID();
        ApiResponse<Void> response = controller.delete(id);
        verify(mappingService).deleteMapping(id);
        assertThat(response.getCode()).isEqualTo(0);
    }

    @Test
    void deleteByLocalItem_shouldReturnCount() {
        UUID itemId = UUID.randomUUID();
        when(mappingService.deleteByLocalItemId(itemId)).thenReturn(3L);

        ApiResponse<Long> response = controller.deleteByLocalItem(itemId);

        assertThat(response.getData()).isEqualTo(3L);
    }

    // ==================== Conflict Detection ====================

    @Test
    void detectAllConflicts_shouldDelegateToService() {
        List<FhirMappingConflictWarning> warnings = List.of();
        when(mappingService.detectAllConflicts()).thenReturn(warnings);

        ApiResponse<List<FhirMappingConflictWarning>> response = controller.detectAllConflicts();

        assertThat(response.getData()).isSameAs(warnings);
    }

    @Test
    void detectConflicts_shouldDelegateToService() {
        UUID itemId = UUID.randomUUID();
        List<FhirMappingConflictWarning> warnings = List.of();
        when(mappingService.detectConflicts(itemId)).thenReturn(warnings);

        ApiResponse<List<FhirMappingConflictWarning>> response = controller.detectConflicts(itemId);

        assertThat(response.getData()).isSameAs(warnings);
    }

    @Test
    void detectConflictsByScheme_shouldDelegateToService() {
        UUID schemeId = UUID.randomUUID();
        List<FhirMappingConflictWarning> warnings = List.of();
        when(mappingService.detectConflictsByScheme(schemeId)).thenReturn(warnings);

        ApiResponse<List<FhirMappingConflictWarning>> response = controller.detectConflictsByScheme(schemeId);

        assertThat(response.getData()).isSameAs(warnings);
    }

    // ==================== ConceptMap Export ====================

    @Test
    void exportAllConceptMap_success_shouldReturnFhirJson() {
        FhirConceptMapExportResult result = FhirConceptMapExportResult.syncSuccess(
                "{\"resourceType\":\"ConceptMap\"}", true, List.of(), 2, 2);
        when(exportService.exportAllMappings()).thenReturn(result);

        ResponseEntity<?> response = controller.exportAllConceptMap();

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("conceptmap-fhir-element-all.json");
    }

    @Test
    void exportAllConceptMap_notFound_shouldReturn404() {
        FhirConceptMapExportResult result = FhirConceptMapExportResult.failure(
                "No FHIR element mappings found for scope: all");
        when(exportService.exportAllMappings()).thenReturn(result);

        ResponseEntity<?> response = controller.exportAllConceptMap();

        assertThat(response.getStatusCodeValue()).isEqualTo(404);
    }

    @Test
    void exportAllConceptMap_failure_shouldReturn400() {
        FhirConceptMapExportResult result = FhirConceptMapExportResult.failure("Export failed");
        when(exportService.exportAllMappings()).thenReturn(result);

        ResponseEntity<?> response = controller.exportAllConceptMap();

        assertThat(response.getStatusCodeValue()).isEqualTo(400);
    }

    @Test
    void exportConceptMapByResourceType_shouldDelegate() {
        FhirConceptMapExportResult result = FhirConceptMapExportResult.syncSuccess(
                "{\"resourceType\":\"ConceptMap\"}", true, List.of(), 1, 1);
        when(exportService.exportByResourceType("Patient")).thenReturn(result);

        ResponseEntity<?> response = controller.exportConceptMapByResourceType("Patient");

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
    }

    @Test
    void exportConceptMapByScheme_shouldDelegate() {
        UUID schemeId = UUID.randomUUID();
        FhirConceptMapExportResult result = FhirConceptMapExportResult.syncSuccess(
                "{\"resourceType\":\"ConceptMap\"}", true, List.of(), 1, 1);
        when(exportService.exportByScheme(schemeId)).thenReturn(result);

        ResponseEntity<?> response = controller.exportConceptMapByScheme(schemeId);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
    }

    // ==================== Helpers ====================

    private FhirElementMapping sampleMapping() {
        FhirElementMapping m = new FhirElementMapping(
                UUID.randomUUID(), "Patient.name.given", "Patient",
                FhirMappingEquivalence.EQUIVALENT);
        m.setId(UUID.randomUUID());
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        m.setVersion(0L);
        return m;
    }
}
