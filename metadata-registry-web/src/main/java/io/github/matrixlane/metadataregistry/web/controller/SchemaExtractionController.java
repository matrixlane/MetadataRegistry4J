package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.schemaextraction.*;
import io.github.matrixlane.metadataregistry.web.service.schemaextraction.SchemaExtractionApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for database schema extraction workflow.
 */
@RestController
@RequestMapping("/api/v1/schema-extraction")
@RequiredArgsConstructor
@Tag(name = "Schema Extraction", description = "Database schema extraction and import")
public class SchemaExtractionController {

    private final SchemaExtractionApplicationService extractionService;

    @PostMapping("/connections")
    @Operation(summary = "Register a database connection")
    public ApiResponse<Void> registerConnection(@Valid @RequestBody DatabaseConnectionRequest request) {
        extractionService.registerConnection(request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/connections/{connectionName}")
    @Operation(summary = "Remove a registered database connection")
    public ApiResponse<Void> removeConnection(@PathVariable String connectionName) {
        extractionService.removeConnection(connectionName);
        return ApiResponse.ok();
    }

    @PostMapping("/connections/{connectionName}/test")
    @Operation(summary = "Test a database connection")
    public ApiResponse<Boolean> testConnection(@PathVariable String connectionName) {
        return ApiResponse.ok(extractionService.testConnection(connectionName));
    }

    @GetMapping("/connections")
    @Operation(summary = "List registered connections")
    public ApiResponse<List<DatabaseConnectionResponse>> listConnections() {
        return ApiResponse.ok(extractionService.listConnections());
    }

    @PostMapping("/extract/{connectionName}")
    @Operation(summary = "Extract schema and return preview")
    public ApiResponse<ExtractionPreviewResponse> extractAndPreview(
            @PathVariable String connectionName,
            @RequestBody SchemaExtractionRequest request) {
        return ApiResponse.ok(extractionService.extractAndPreview(connectionName, request));
    }

    @GetMapping("/preview/{sessionId}")
    @Operation(summary = "Get extraction preview by session ID")
    public ApiResponse<ExtractionPreviewResponse> getPreview(@PathVariable UUID sessionId) {
        return ApiResponse.ok(extractionService.getPreview(sessionId));
    }

    @PostMapping("/import")
    @Operation(summary = "Import selected extracted items into the registry")
    public ApiResponse<SchemaImportResponse> importSelected(@Valid @RequestBody SchemaImportRequest request) {
        return ApiResponse.ok(extractionService.importSelected(request));
    }
}
