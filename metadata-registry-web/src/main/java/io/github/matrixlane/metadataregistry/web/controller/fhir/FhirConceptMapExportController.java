package io.github.matrixlane.metadataregistry.web.controller.fhir;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirConceptMapExportResult;
import io.github.matrixlane.metadataregistry.web.service.fhir.FhirConceptMapExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for FHIR ConceptMap export (WI-011-2-2).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/mappings/export/fhir} — export by schemeId</li>
 *   <li>{@code GET /api/v1/mappings/export/fhir/tasks/{taskId}} — check async task status</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/mappings/export/fhir")
@Tag(name = "12. FHIR ConceptMap 导出", description = "ISO/IEC 11179 Clause 11 — 映射关系 FHIR ConceptMap 导出（WI-011-2-2）")
public class FhirConceptMapExportController {

    private final FhirConceptMapExportService exportService;

    private static final UUID DEFAULT_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    public FhirConceptMapExportController(FhirConceptMapExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping
    @Operation(summary = "导出 FHIR ConceptMap",
            description = "将映射关系导出为 FHIR R4 ConceptMap JSON。"
                    + "当映射条目数超过阈值时自动切换为异步导出。"
                    + "通过 schemeId=all 可导出全部映射方案。")
    public ResponseEntity<?> exportConceptMap(
            @Parameter(description = "映射方案 ID（UUID）或 'all'", required = true, example = "all")
            @RequestParam String schemeId,
            @Parameter(hidden = true) Principal principal) {

        if (schemeId == null || schemeId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "schemeId is required"));
        }

        UUID userId = Optional.ofNullable(principal)
                .map(p -> {
                    try {
                        return UUID.fromString(p.getName());
                    } catch (IllegalArgumentException e) {
                        return DEFAULT_USER_ID;
                    }
                })
                .orElse(DEFAULT_USER_ID);

        FhirConceptMapExportResult result = exportService.exportConceptMap(schemeId, userId);

        if (!result.success()) {
            if (result.errorMessage() != null &&
                    result.errorMessage().contains("No mappings found")) {
                return ResponseEntity.status(404)
                        .body(ApiResponse.error(404, result.errorMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, result.errorMessage()));
        }

        if (result.async()) {
            return ResponseEntity.accepted()
                    .body(ApiResponse.ok(result));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"conceptmap-" + schemeId + ".json\"")
                .contentType(MediaType.valueOf("application/fhir+json"))
                .body(result.conceptMapJson());
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查询异步导出任务状态",
            description = "通过任务 ID 查询异步导出任务的当前状态、进度和结果。")
    public ResponseEntity<ApiResponse<FhirConceptMapExportResult>> getExportTaskStatus(
            @Parameter(description = "导出任务 ID", required = true)
            @PathVariable UUID taskId) {

        FhirConceptMapExportResult result = exportService.getExportTaskStatus(taskId);

        if (!result.success() && result.errorMessage() != null
                && result.errorMessage().contains("not found")) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(404, result.errorMessage()));
        }

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
