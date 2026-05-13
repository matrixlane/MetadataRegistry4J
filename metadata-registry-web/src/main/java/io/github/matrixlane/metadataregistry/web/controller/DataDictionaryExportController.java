package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.export.ExportFormat;
import io.github.matrixlane.metadataregistry.web.dto.export.datadictionary.DataDictionaryExportRequest;
import io.github.matrixlane.metadataregistry.web.dto.export.ExportTaskResponse;
import io.github.matrixlane.metadataregistry.web.entity.ExportTask;
import io.github.matrixlane.metadataregistry.web.entity.ExportTaskStatus;
import io.github.matrixlane.metadataregistry.web.service.datadictionary.DataDictionaryExportService;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * 数据字典导出 API
 *
 * <p>支持以下导出能力：
 * <ul>
 *   <li><b>Excel 导出</b> — 数据字典表格，含值域明细</li>
 *   <li><b>PDF 导出</b> — 单数据元详情或完整数据字典报告</li>
 *   <li><b>异步导出</b> — 大数据量后台生成，支持进度查询和下载</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/export/data-dictionary")
@RequiredArgsConstructor
@Tag(name = "11. 数据字典导出", description = "数据字典 Excel/PDF 导出，支持同步和异步模式")
public class DataDictionaryExportController {

    private final DataDictionaryExportService exportService;

    // ─────────────────────────────────────────────────────────────────
    // 同步导出
    // ─────────────────────────────────────────────────────────────────

    @PostMapping(value = "/excel", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "同步导出 Excel", description = "直接返回 Excel 文件。最多支持 5,000 条记录。")
    public ResponseEntity<byte[]> exportExcel(@Valid @RequestBody DataDictionaryExportRequest request) throws IOException {
        if (request.format() != ExportFormat.EXCEL) {
            throw new IllegalArgumentException("此接口仅支持 Excel 格式，请设置 format=EXCEL");
        }
        return buildFileResponse(
                exportService.exportSync(request),
                generateFilename(request, "xlsx"),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
    }

    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "同步导出 PDF", description = "直接返回 PDF 文件。单条导出为详情页，多条为完整报告。最多支持 5,000 条记录。")
    public ResponseEntity<byte[]> exportPdf(@Valid @RequestBody DataDictionaryExportRequest request) throws IOException {
        if (request.format() != ExportFormat.PDF) {
            throw new IllegalArgumentException("此接口仅支持 PDF 格式，请设置 format=PDF");
        }
        return buildFileResponse(
                exportService.exportSync(request),
                generateFilename(request, "pdf"),
                MediaType.APPLICATION_PDF_VALUE
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // 异步导出
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/async")
    @Operation(summary = "创建异步导出任务", description = "提交异步导出请求，返回任务ID。任务完成后可下载结果。")
    public ApiResponse<ExportTaskResponse> createAsyncExport(
            @Valid @RequestBody DataDictionaryExportRequest request,
            Principal principal) {
        UUID userId = extractUserId(principal);
        ExportTask task = exportService.createAsyncExportTask(request, userId);

        // 启动异步执行
        exportService.executeAsyncExport(task.getId());

        return ApiResponse.ok(ExportTaskResponse.from(task));
    }

    @GetMapping("/async/{taskId}")
    @Operation(summary = "查询异步导出任务状态")
    public ApiResponse<ExportTaskResponse> getAsyncExportStatus(@PathVariable UUID taskId) {
        ExportTask task = exportService.getTaskStatus(taskId);
        long fileSize = task.getStatus() == ExportTaskStatus.COMPLETED
                ? exportService.getTaskResultSize(taskId) : 0;
        return ApiResponse.ok(ExportTaskResponse.from(task, fileSize));
    }

    @GetMapping("/async/{taskId}/download")
    @Operation(summary = "下载异步导出结果")
    public ResponseEntity<byte[]> downloadAsyncExport(@PathVariable UUID taskId) throws IOException {
        ExportTask task = exportService.getTaskStatus(taskId);

        if (task.getStatus() != ExportTaskStatus.COMPLETED) {
            throw new IllegalStateException("导出任务未完成，当前状态: " + task.getStatus());
        }

        byte[] content = exportService.downloadTaskResult(taskId);
        String ext = task.getFormat() != null ? task.getFormat().toLowerCase() : "bin";
        String mime = switch (ext) {
            case "excel", "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pdf" -> MediaType.APPLICATION_PDF_VALUE;
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };

        return buildFileResponse(content, "data-dictionary-export-" + taskId + "." + ext, mime);
    }

    // ─────────────────────────────────────────────────────────────────
    // 辅助方法
    // ─────────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> buildFileResponse(byte[] content, String filename, String contentType) {
        // 文件名安全处理：替换特殊字符
        String safeFilename = sanitizeFilename(filename);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(safeFilename, StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(content.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    private String generateFilename(DataDictionaryExportRequest request, String extension) {
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return "data-dictionary_" + request.format().name().toLowerCase() + "_" + timestamp + "." + extension;
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "export.bin";
        }
        // 替换 Windows/Unix 非法字符
        return filename.replaceAll("[<>:\\\"|?*]", "_")
                .replaceAll("\\.{2,}", ".")
                .replaceAll("\s+", "_");
    }

    private UUID extractUserId(Principal principal) {
        if (principal == null) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        return UUID.nameUUIDFromBytes(principal.getName().getBytes(StandardCharsets.UTF_8));
    }
}
