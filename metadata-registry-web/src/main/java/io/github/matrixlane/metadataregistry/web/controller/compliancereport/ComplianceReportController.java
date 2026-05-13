package io.github.matrixlane.metadataregistry.web.controller.compliancereport;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.ComplianceReport;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.ExportConfig;
import io.github.matrixlane.metadataregistry.web.service.compliancereport.ComplianceReportService;
import io.github.matrixlane.metadataregistry.web.service.compliancereport.ExcelExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

/**
 * 合规报告生成与导出 REST 控制器 (WI-013-3).
 *
 * <p>提供互联互通标准化成熟度测评合规报告的生成、预览和导出功能。
 *
 * <p>MVP 范围（按架构评审决策）：
 * <ul>
 *   <li>仅支持 Excel 导出（Word 延后到 V1）</li>
 *   <li>报告结构硬编码（不引入模板引擎）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/compliance/report")
@Tag(name = "13. 互联互通合规报告", description = "WI-013-3 — 合规报告生成与导出")
public class ComplianceReportController {

    private final ComplianceReportService reportService;
    private final ExcelExportService excelExportService;

    public ComplianceReportController(
            ComplianceReportService reportService,
            ExcelExportService excelExportService) {
        this.reportService = reportService;
        this.excelExportService = excelExportService;
    }

    // ─────────────────────────────────────────────────────────────────
    // 1. 预览报告（JSON）
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/preview")
    @Operation(summary = "预览合规报告",
            description = "根据导出配置生成合规报告数据并返回 JSON，供前端预览。")
    public ApiResponse<ComplianceReport> previewReport(
            @RequestBody(required = false) ExportConfig config,
            Principal principal) {
        ExportConfig effectiveConfig = (config != null) ? config : ExportConfig.defaults();
        ComplianceReport report = reportService.generateReport(effectiveConfig, principal);
        return ApiResponse.ok(report);
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. 导出 Excel
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/export/excel")
    @Operation(summary = "导出合规报告为 Excel",
            description = "根据导出配置生成合规报告并导出为 .xlsx 文件。")
    public void exportExcel(
            @RequestBody(required = false) ExportConfig config,
            HttpServletResponse response,
            Principal principal) throws IOException {

        ExportConfig effectiveConfig = (config != null) ? config : ExportConfig.defaults();
        ComplianceReport report = reportService.generateReport(effectiveConfig, principal);

        // 设置响应头
        String filename = "合规报告_" + java.time.LocalDate.now() + ".xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encodedFilename);
        response.setHeader("X-Report-Title", report.title());

        excelExportService.exportToExcel(report, response.getOutputStream());
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. 快速导出（GET，默认配置）
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/export/excel")
    @Operation(summary = "快速导出合规报告（默认配置）",
            description = "使用默认导出配置快速生成 Excel 报告。")
    public void exportExcelQuick(
            @RequestParam(required = false) List<String> dataSetCodes,
            @RequestParam(required = false) List<String> matchStatuses,
            HttpServletResponse response,
            Principal principal) throws IOException {

        ExportConfig config = new ExportConfig(
                dataSetCodes != null ? dataSetCodes : List.of(),
                matchStatuses != null ? matchStatuses : List.of(),
                "互联互通标准化成熟度测评合规报告", "");

        ComplianceReport report = reportService.generateReport(config, principal);

        String filename = "合规报告_" + java.time.LocalDate.now() + ".xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encodedFilename);

        excelExportService.exportToExcel(report, response.getOutputStream());
    }
}
