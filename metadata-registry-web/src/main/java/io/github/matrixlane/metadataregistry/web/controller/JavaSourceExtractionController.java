package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.javasource.JavaSourceExtractionRequest;
import io.github.matrixlane.metadataregistry.web.dto.javasource.JavaSourceExtractionResponse;
import io.github.matrixlane.metadataregistry.web.dto.javasource.JavaSourceMappingPreviewResponse;
import io.github.matrixlane.metadataregistry.web.service.JavaSourceExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for extracting JavaDoc comments from Java source files
 * and mapping them to ISO/IEC 11179 Administered Items.
 */
@RestController
@RequestMapping("/api/v1/java-source-extraction")
@RequiredArgsConstructor
@Tag(name = "Java 源码注释提取", description = "从 Java 源码注释中提取数据定义并映射为 Administered Item")
public class JavaSourceExtractionController {

    private final JavaSourceExtractionService extractionService;

    @PostMapping("/extract")
    @Operation(summary = "提取 Java 源码注释", description = "支持 TEXT/FILE/DIRECTORY/ZIP 四种模式")
    public ApiResponse<JavaSourceExtractionResponse> extract(@Valid @RequestBody JavaSourceExtractionRequest request) {
        return ApiResponse.ok(extractionService.extract(request));
    }

    @PostMapping("/preview")
    @Operation(summary = "预览映射结果", description = "预览提取的注释将映射为哪些 Administered Item")
    public ApiResponse<JavaSourceMappingPreviewResponse> previewMapping(@Valid @RequestBody JavaSourceExtractionRequest request) {
        return ApiResponse.ok(extractionService.previewMapping(request));
    }
}
