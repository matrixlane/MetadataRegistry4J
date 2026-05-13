package io.github.matrixlane.metadataregistry.web.controller.fhir;

import io.github.matrixlane.metadataregistry.itemmapping.dto.FhirMappingConflictWarning;
import io.github.matrixlane.metadataregistry.itemmapping.entity.FhirElementMapping;
import io.github.matrixlane.metadataregistry.itemmapping.service.FhirElementMappingService;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirConceptMapExportResult;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirElementMappingRequest;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirElementMappingResponse;
import io.github.matrixlane.metadataregistry.web.service.fhir.FhirElementMappingExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for local AdministeredItem ↔ FHIR Element mapping management (WI-012-2).
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>CRUD: list, get, create, update, delete</li>
 *   <li>Conflict detection: per-item, all, by-scheme</li>
 *   <li>ConceptMap export: all, by resource type, by scheme</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/fhir-element-mappings")
@RequiredArgsConstructor
@Tag(name = "13. 本地元数据与 FHIR Element 映射", description = "本地 AdministeredItem ↔ FHIR Element 映射 CRUD、冲突检测与 ConceptMap 导出（WI-012-2）")
public class FhirElementMappingController {

    private final FhirElementMappingService mappingService;
    private final FhirElementMappingExportService exportService;

    private static final UUID DEFAULT_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    // ==================== CRUD ====================

    @GetMapping
    @Operation(summary = "列出所有 FHIR Element 映射",
            description = "支持按本地项 ID、Resource 类型或映射方案 ID 筛选")
    public ApiResponse<List<FhirElementMappingResponse>> list(
            @RequestParam(required = false) @Parameter(description = "本地项 ID") UUID localItemId,
            @RequestParam(required = false) @Parameter(description = "FHIR Resource 类型") String resourceType,
            @RequestParam(required = false) @Parameter(description = "映射方案 ID") UUID schemeId) {

        List<FhirElementMapping> mappings;
        if (localItemId != null) {
            mappings = mappingService.findByLocalItemId(localItemId);
        } else if (resourceType != null && !resourceType.isBlank()) {
            mappings = mappingService.findByResourceType(resourceType);
        } else if (schemeId != null) {
            mappings = mappingService.findBySchemeId(schemeId);
        } else {
            mappings = mappingService.findAll();
        }

        return ApiResponse.ok(mappings.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单个映射详情")
    public ApiResponse<FhirElementMappingResponse> get(@PathVariable UUID id) {
        FhirElementMapping mapping = mappingService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + id));
        return ApiResponse.ok(toResponse(mapping));
    }

    @PostMapping
    @Operation(summary = "创建 FHIR Element 映射",
            description = "支持一对一和一对多映射类型。一对多映射必须提供 mappingVariable。")
    public ApiResponse<FhirElementMappingResponse> create(
            @Valid @RequestBody FhirElementMappingRequest request,
            @Parameter(hidden = true) Principal principal) {

        UUID userId = resolveUserId(principal);

        FhirElementMapping mapping;
        if (request.mappingType() == io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingType.ONE_TO_MANY) {
            mapping = mappingService.createOneToManyMapping(
                    request.localItemId(),
                    request.fhirPath(),
                    request.fhirResourceType(),
                    request.fhirEquivalence(),
                    request.mappingVariable(),
                    request.description(),
                    request.mappingSchemeId(),
                    userId
            );
        } else {
            mapping = mappingService.createOneToOneMapping(
                    request.localItemId(),
                    request.fhirPath(),
                    request.fhirResourceType(),
                    request.fhirEquivalence(),
                    request.description(),
                    request.mappingSchemeId(),
                    userId
            );
        }

        return ApiResponse.ok(toResponse(mapping));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 FHIR Element 映射")
    public ApiResponse<FhirElementMappingResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody FhirElementMappingRequest request,
            @Parameter(hidden = true) Principal principal) {

        UUID userId = resolveUserId(principal);

        FhirElementMapping mapping = mappingService.updateMapping(
                id,
                request.fhirPath(),
                request.fhirEquivalence(),
                request.mappingType(),
                request.mappingVariable(),
                request.description(),
                userId
        );

        return ApiResponse.ok(toResponse(mapping));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除单个映射")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        mappingService.deleteMapping(id);
        return ApiResponse.ok();
    }

    @DeleteMapping
    @Operation(summary = "按本地项 ID 批量删除映射")
    public ApiResponse<Long> deleteByLocalItem(
            @RequestParam @Parameter(description = "本地项 ID") UUID localItemId) {
        long count = mappingService.deleteByLocalItemId(localItemId);
        return ApiResponse.ok(count);
    }

    // ==================== Conflict Detection ====================

    @GetMapping("/conflicts")
    @Operation(summary = "检测全部映射冲突",
            description = "返回所有本地项映射到多个 FHIR Element 的冲突警告")
    public ApiResponse<List<FhirMappingConflictWarning>> detectAllConflicts() {
        return ApiResponse.ok(mappingService.detectAllConflicts());
    }

    @GetMapping("/{localItemId}/conflicts")
    @Operation(summary = "检测指定本地项的映射冲突")
    public ApiResponse<List<FhirMappingConflictWarning>> detectConflicts(
            @PathVariable @Parameter(description = "本地项 ID") UUID localItemId) {
        return ApiResponse.ok(mappingService.detectConflicts(localItemId));
    }

    @GetMapping("/schemes/{schemeId}/conflicts")
    @Operation(summary = "检测指定映射方案内的冲突")
    public ApiResponse<List<FhirMappingConflictWarning>> detectConflictsByScheme(
            @PathVariable @Parameter(description = "映射方案 ID") UUID schemeId) {
        return ApiResponse.ok(mappingService.detectConflictsByScheme(schemeId));
    }

    // ==================== ConceptMap Export ====================

    @GetMapping("/export/conceptmap")
    @Operation(summary = "导出全部映射为 FHIR ConceptMap",
            description = "将所有 FHIR Element 映射导出为 FHIR R4 ConceptMap JSON，使用 HAPI FHIR 验证格式")
    public ResponseEntity<?> exportAllConceptMap() {
        return buildExportResponse(exportService.exportAllMappings(), "all");
    }

    @GetMapping("/export/conceptmap/{resourceType}")
    @Operation(summary = "按 Resource 类型导出 FHIR ConceptMap")
    public ResponseEntity<?> exportConceptMapByResourceType(
            @PathVariable @Parameter(description = "FHIR Resource 类型，如 Patient") String resourceType) {
        return buildExportResponse(exportService.exportByResourceType(resourceType), resourceType);
    }

    @GetMapping("/schemes/{schemeId}/export/conceptmap")
    @Operation(summary = "按映射方案导出 FHIR ConceptMap")
    public ResponseEntity<?> exportConceptMapByScheme(
            @PathVariable @Parameter(description = "映射方案 ID") UUID schemeId) {
        return buildExportResponse(exportService.exportByScheme(schemeId), schemeId.toString());
    }

    // ==================== Private helpers ====================

    private UUID resolveUserId(Principal principal) {
        return Optional.ofNullable(principal)
                .map(p -> {
                    try {
                        return UUID.fromString(p.getName());
                    } catch (IllegalArgumentException e) {
                        return DEFAULT_USER_ID;
                    }
                })
                .orElse(DEFAULT_USER_ID);
    }

    private FhirElementMappingResponse toResponse(FhirElementMapping m) {
        return new FhirElementMappingResponse(
                m.getId(),
                m.getLocalItemId(),
                m.getFhirPath(),
                m.getFhirResourceType(),
                m.getFhirEquivalence(),
                m.getMappingType(),
                m.getMappingVariable(),
                m.getDescription(),
                m.getMappingSchemeId(),
                m.getCreatedBy(),
                m.getUpdatedBy(),
                m.getCreatedAt(),
                m.getUpdatedAt(),
                m.getVersion()
        );
    }

    private ResponseEntity<?> buildExportResponse(FhirConceptMapExportResult result, String scope) {
        if (!result.success()) {
            if (result.errorMessage() != null &&
                    result.errorMessage().contains("No FHIR element mappings found")) {
                return ResponseEntity.status(404)
                        .body(ApiResponse.error(404, result.errorMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, result.errorMessage()));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"conceptmap-fhir-element-" + scope + ".json\"")
                .contentType(MediaType.valueOf("application/fhir+json"))
                .body(result.conceptMapJson());
    }
}
