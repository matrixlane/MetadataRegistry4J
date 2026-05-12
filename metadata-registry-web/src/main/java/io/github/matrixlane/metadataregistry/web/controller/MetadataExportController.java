package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.export.ExportTaskResponse;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportRequest;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportResult;
import io.github.matrixlane.metadataregistry.web.entity.ExportTask;
import io.github.matrixlane.metadataregistry.web.repository.ExportTaskRepository;
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

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * 注册元数据导出 API
 *
 * <p>支持以下导出模式：
 * <ul>
 *   <li><b>同步导出</b>（GET /api/v1/export） - 小数据量直接返回 JSON</li>
 *   <li><b>异步导出</b>（POST /api/v1/export/async） - 大数据量后台生成，通过任务ID获取结果</li>
 *   <li><b>流式导出</b>（GET /api/v1/export/stream） - 大文件通过输出流直接返回</li>
 *   <li><b>CSV 导出</b>（GET /api/v1/export/csv） - 以 CSV 格式导出</li>
 *   <li><b>分片下载</b>（GET /api/v1/export/async/{taskId}/chunk） - HTTP Range 风格分片</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/export/metadata")
@RequiredArgsConstructor
@Tag(name = "10. 注册元数据导出", description = "元数据导出，支持 JSON/CSV 格式、过滤条件、字段筛选和异步导出")
public class MetadataExportController {

    private final MetadataExportService exportService;
    private final ExportTaskRepository exportTaskRepository;

    // ---- 同步导出 ----

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "同步导出注册元数据",
            description = "直接返回 JSON 格式的导出结果。当匹配记录超过阈值(10000条)时返回建议使用异步导出的提示。")
    public ApiResponse<?> exportSync(@Valid @RequestBody MetadataExportRequest request) {
        if (exportService.shouldUseAsyncExport(request)) {
            return ApiResponse.ok(new SyncExportOverflowResponse(
                    "匹配记录超过同步导出上限，请使用异步导出",
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

    // ---- 异步导出 ----

    @PostMapping("/async")
    @Operation(summary = "创建异步导出任务",
            description = "提交异步导出请求，返回任务ID。任务完成后可通过该ID下载结果。")
    public ApiResponse<ExportTaskResponse> createAsyncExport(
            @Valid @RequestBody MetadataExportRequest request,
            Principal principal) {
        UUID userId = extractUserId(principal);
        ExportTask task = exportService.createAsyncExportTask(request, userId);

        // 异步执行
        exportService.executeAsyncExport(task.getId());

        return ApiResponse.ok(ExportTaskResponse.from(task));
    }

    @GetMapping("/async/{taskId}")
    @Operation(summary = "查询异步导出任务状态")
    public ApiResponse<ExportTaskResponse> getAsyncExportStatus(
            @PathVariable UUID taskId) {
        ExportTask task = exportTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("导出任务不存在: " + taskId));

        long fileSize = 0;
        if (task.getStatus() == io.github.matrixlane.metadataregistry.web.entity.ExportTaskStatus.COMPLETED) {
            fileSize = exportService.getExportFileSize(taskId);
        }

        return ApiResponse.ok(ExportTaskResponse.from(task, fileSize));
    }

    @GetMapping("/async/{taskId}/download")
    @Operation(summary = "下载异步导出结果",
            description = "下载已完成的异步导出结果文件。任务完成后在有效期内可下载。")
    public ResponseEntity<byte[]> downloadAsyncExport(
            @PathVariable UUID taskId,
            @Parameter(description = "文件分片偏移量（字节）") @RequestParam(required = false, defaultValue = "0") Long offset,
            @Parameter(description = "文件分片长度（字节）") @RequestParam(required = false, defaultValue = "4194304") Integer length) {
        ExportTask task = exportTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("导出任务不存在: " + taskId));

        if (task.getStatus() != io.github.matrixlane.metadataregistry.web.entity.ExportTaskStatus.COMPLETED) {
            throw new IllegalStateException("导出任务未完成，当前状态: " + task.getStatus());
        }

        long fileSize = exportService.getExportFileSize(taskId);
        byte[] chunk = exportService.readExportChunk(taskId, offset, Math.min(length, (int) (fileSize - offset)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("metadata-export-" + taskId + ".json")
                .build());
        headers.setContentLength(chunk.length);
        headers.add("X-Export-File-Size", String.valueOf(fileSize));
        headers.add("X-Export-Chunk-Offset", String.valueOf(offset));
        headers.add("X-Export-Chunk-Length", String.valueOf(chunk.length));
        headers.add("X-Export-Task-Status", task.getStatus().name());

        // Support Range-like behavior
        if (offset > 0 || chunk.length < fileSize) {
            headers.add("Accept-Ranges", "bytes");
            headers.add("Content-Range", "bytes " + offset + "-" + (offset + chunk.length - 1) + "/" + fileSize);
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(chunk);
    }

    // ---- 辅助方法 ----

    /**
     * 从 Principal 提取用户 ID
     * <p>
     * 实际实现应基于 JWT token 中的用户信息。当前简化处理。
     */
    private UUID extractUserId(Principal principal) {
        // 实际项目中需要从 Authentication/UserDetails 中提取用户ID
        // 此处做简化，从 principal name 生成一个 UUID
        if (principal == null) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        return UUID.nameUUIDFromBytes(principal.getName().getBytes());
    }
}
