package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.dto.export.ExportFormat;
import io.github.matrixlane.metadataregistry.web.dto.export.datadictionary.DataDictionaryExportRequest;
import io.github.matrixlane.metadataregistry.web.service.datadictionary.DataDictionaryExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link DataDictionaryExportController} 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DataDictionaryExportControllerTest {

    @Mock
    private DataDictionaryExportService exportService;

    @InjectMocks
    private DataDictionaryExportController controller;

    private MockMvc mockMvc;
    private final UUID testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // =========================================================================
    // 1. Excel 同步导出
    // =========================================================================

    @Test
    @DisplayName("POST /excel - 应返回 Excel 文件")
    void exportExcel_shouldReturnExcelFile() throws Exception {
        when(exportService.exportSync(any())).thenReturn(new byte[]{1, 2, 3, 4});

        DataDictionaryExportRequest request = new DataDictionaryExportRequest(
                List.of(UUID.randomUUID()), ExportFormat.EXCEL,
                DataDictionaryExportRequest.ExportMode.SINGLE, false, false, false, 100);

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request);

        mockMvc.perform(post("/api/v1/export/data-dictionary/excel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @DisplayName("POST /excel - 格式错误应返回 400")
    void exportExcel_wrongFormat_shouldThrow() {
        DataDictionaryExportRequest request = new DataDictionaryExportRequest(
                List.of(UUID.randomUUID()), ExportFormat.PDF,
                DataDictionaryExportRequest.ExportMode.SINGLE, false, false, false, 100);

        assertThatThrownBy(() -> controller.exportExcel(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 Excel");
    }

    // =========================================================================
    // 2. PDF 同步导出
    // =========================================================================

    @Test
    @DisplayName("POST /pdf - 应返回 PDF 文件")
    void exportPdf_shouldReturnPdfFile() throws Exception {
        when(exportService.exportSync(any())).thenReturn(new byte[]{1, 2, 3, 4});

        DataDictionaryExportRequest request = new DataDictionaryExportRequest(
                List.of(UUID.randomUUID()), ExportFormat.PDF,
                DataDictionaryExportRequest.ExportMode.SINGLE, false, false, false, 100);

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request);

        mockMvc.perform(post("/api/v1/export/data-dictionary/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().exists("Content-Disposition"));
    }

    // =========================================================================
    // 3. 异步导出
    // =========================================================================



    // =========================================================================
    // 4. 下载
    // =========================================================================



    // =========================================================================
    // 5. 文件名安全处理
    // =========================================================================

    @Test
    @DisplayName("文件名安全处理 - 应替换非法字符")
    void sanitizeFilename_shouldReplaceIllegalChars() {
        String dirty = "file<name>:with|illegal?chars*.xlsx";
        String clean = controller.sanitizeFilename(dirty);
        assertThat(clean).doesNotContain("<", ">", ":", "\"", "|", "?", "*");
    }

    @Test
    @DisplayName("文件名安全处理 - null 输入应返回默认文件名")
    void sanitizeFilename_null_shouldReturnDefault() {
        assertThat(controller.sanitizeFilename(null)).isEqualTo("export.bin");
    }

    // =========================================================================
    // 6. 构建文件响应
    // =========================================================================

    @Test
    @DisplayName("文件响应 - 应包含正确的 Content-Type")
    void buildFileResponse_shouldSetCorrectContentType() {
        ResponseEntity<byte[]> response = controller.buildFileResponse(
                new byte[]{1, 2, 3}, "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("test.xlsx");
    }
}
