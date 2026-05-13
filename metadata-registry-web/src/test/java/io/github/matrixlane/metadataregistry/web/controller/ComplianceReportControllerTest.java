package io.github.matrixlane.metadataregistry.web.controller.compliancereport;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.ComplianceReport;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.ExportConfig;
import io.github.matrixlane.metadataregistry.web.service.compliancereport.ComplianceReportService;
import io.github.matrixlane.metadataregistry.web.service.compliancereport.ExcelExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ComplianceReportController} 单元测试.
 */
@ExtendWith(MockitoExtension.class)
class ComplianceReportControllerTest {

    @Mock
    private ComplianceReportService reportService;

    @Mock
    private ExcelExportService excelExportService;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Principal principal;

    private ComplianceReportController controller;

    private ComplianceReport sampleReport;

    @BeforeEach
    void setUp() {
        controller = new ComplianceReportController(reportService, excelExportService);

        sampleReport = new ComplianceReport(
                "互联互通测评合规报告",
                LocalDateTime.of(2026, 5, 13, 8, 0),
                "testuser",
                List.of(),
                null);
    }

    // =========================================================================
    // 1. 预览报告
    // =========================================================================

    @Test
    @DisplayName("POST 预览报告 - 正常返回")
    void previewReport_shouldReturnReport() {
        when(reportService.generateReport(any(ExportConfig.class), eq(principal)))
                .thenReturn(sampleReport);

        ExportConfig config = ExportConfig.defaults();
        ApiResponse<ComplianceReport> result = controller.previewReport(config, principal);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().title()).isEqualTo("互联互通测评合规报告");
        assertThat(result.getData().generatedBy()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("POST 预览报告 - config 为 null 时使用默认配置")
    void previewReport_withNullConfig_shouldUseDefaults() {
        when(reportService.generateReport(any(ExportConfig.class), eq(principal)))
                .thenReturn(sampleReport);

        ApiResponse<ComplianceReport> result = controller.previewReport(null, principal);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(0);
    }

    @Test
    @DisplayName("POST 预览报告 - principal 为 null 时不报错")
    void previewReport_withNullPrincipal_shouldNotThrow() {
        when(reportService.generateReport(any(ExportConfig.class), eq(null)))
                .thenReturn(sampleReport);

        ApiResponse<ComplianceReport> result = controller.previewReport(ExportConfig.defaults(), null);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(0);
    }

    // =========================================================================
    // 2. 导出 Excel
    // =========================================================================

    @Test
    @DisplayName("POST 导出 Excel - 应调用导出服务")
    void exportExcel_shouldCallExportService() throws Exception {
        when(reportService.generateReport(any(ExportConfig.class), eq(principal)))
                .thenReturn(sampleReport);

        // Capture the output stream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public void write(int b) { baos.write(b); }
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(jakarta.servlet.WriteListener listener) {}
        });

        doAnswer(inv -> {
            OutputStream os = inv.getArgument(1);
            os.write("EXCEL_CONTENT".getBytes());
            return null;
        }).when(excelExportService).exportToExcel(any(ComplianceReport.class), any(OutputStream.class));

        controller.exportExcel(ExportConfig.defaults(), response, principal);

        // Verify service was called
        verify(excelExportService).exportToExcel(eq(sampleReport), any(OutputStream.class));
        assertThat(baos.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("POST 导出 Excel - config 为 null 时使用默认配置")
    void exportExcel_withNullConfig_shouldUseDefaults() throws Exception {
        when(reportService.generateReport(any(ExportConfig.class), eq(principal)))
                .thenReturn(sampleReport);

        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public void write(int b) {}
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(jakarta.servlet.WriteListener listener) {}
        });

        controller.exportExcel(null, response, principal);

        ArgumentCaptor<ExportConfig> configCaptor = ArgumentCaptor.forClass(ExportConfig.class);
        verify(reportService).generateReport(configCaptor.capture(), eq(principal));
        ExportConfig captured = configCaptor.getValue();
        assertThat(captured.reportTitle()).isEqualTo("互联互通标准化成熟度测评合规报告");
    }

    // =========================================================================
    // 3. 快速导出（GET）
    // =========================================================================

    @Test
    @DisplayName("GET 快速导出 - 应支持参数筛选")
    void exportExcelQuick_shouldSupportParams() throws Exception {
        when(reportService.generateReport(any(ExportConfig.class), eq(principal)))
                .thenReturn(sampleReport);

        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public void write(int b) {}
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(jakarta.servlet.WriteListener listener) {}
        });

        controller.exportExcelQuick(
                List.of("HDSB01.01"), List.of("已匹配"),
                response, principal);

        ArgumentCaptor<ExportConfig> configCaptor = ArgumentCaptor.forClass(ExportConfig.class);
        verify(reportService).generateReport(configCaptor.capture(), eq(principal));
        ExportConfig config = configCaptor.getValue();
        assertThat(config.dataSetCodes()).contains("HDSB01.01");
        assertThat(config.matchStatuses()).contains("已匹配");
    }

    @Test
    @DisplayName("GET 快速导出 - 无参数时使用空列表")
    void exportExcelQuick_withoutParams_shouldUseEmptyLists() throws Exception {
        when(reportService.generateReport(any(ExportConfig.class), eq(principal)))
                .thenReturn(sampleReport);

        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public void write(int b) {}
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(jakarta.servlet.WriteListener listener) {}
        });

        controller.exportExcelQuick(null, null, response, principal);

        ArgumentCaptor<ExportConfig> configCaptor = ArgumentCaptor.forClass(ExportConfig.class);
        verify(reportService).generateReport(configCaptor.capture(), eq(principal));
        ExportConfig config = configCaptor.getValue();
        assertThat(config.dataSetCodes()).isEmpty();
        assertThat(config.matchStatuses()).isEmpty();
    }
}
