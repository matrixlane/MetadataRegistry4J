package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportRequest;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportResult;
import io.github.matrixlane.metadataregistry.web.service.MetadataExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 注册元数据导出 API
 *
 * <p>支持以下导出模式：
 * <ul>
 *   <li><b>同步导出</b>（POST /api/v1/export/metadata） - 小数据量直接返回 JSON</li>
 *   <li><b>流式导出</b>（GET /api/v1/export/metadata/stream） - 大文件通过输出流直接返回</li>
 *   <li><b>CSV 导出</b>（GET /api/v1/export/metadata/csv） - 以 CSV 格式导出</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/export/metadata")
@RequiredArgsConstructor
@Tag(name = "10. 注册元数据导出", description = "元数据导出，支持 JSON/CSV 格式、过滤条件、字段筛选")
public class MetadataExportController {

    private final MetadataExportService exportService;

    // ---- 同步导出 ----

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "同步导出注册元数据",
            description = "直接返回 JSON 格式的导出结果。当匹配记录超过阈值(10000条)时返回建议使用流式导出的提示。")
    public ApiResponse<?> exportSync(@Valid @RequestBody MetadataExportRequest request) {
        if (exportService.shouldUseAsyncExport(request)) {
            return ApiResponse.ok(new SyncExportOverflowResponse(
                    "匹配记录超过同步导出上限，请使用流式导出(GET /api/v1/export/metadata/stream)",
                    exportService.estimateCount(request)));
        }
        try {
            MetadataExportResult result = exportService.export(request);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }

    /**
     * 同步导出结果超限时的响应
     */
    public record SyncExportOverflowResponse(
            String message,
            long estimatedCount
    ) {}

    // ---- 流式导出（同步，无大小限制） ----

    @GetMapping(value = "/stream", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "流式导出 JSON", description = "以流式响应返回 JSON 导出结果，适合中大型数据集(<10万条)")
    public ResponseEntity<byte[]> exportStream(@Valid MetadataExportRequest request) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(4096);
            exportService.exportJsonToStream(request, baos);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("metadata-export.json")
                    .build());
            headers.setContentLength(baos.size());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("流式导出失败", e);
        }
    }

    @GetMapping(value = "/csv")
    @Operation(summary = "导出 CSV", description = "以 CSV 格式导出注册元数据，含 BOM 头（兼容 Excel）")
    public ResponseEntity<byte[]> exportCsv(@Valid MetadataExportRequest request) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(4096);
            exportService.exportCsvToStream(request, baos);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("metadata-export.csv")
                    .build());
            headers.setContentLength(baos.size());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("CSV导出失败", e);
        }
    }

    // ---- 辅助方法 ----


}
