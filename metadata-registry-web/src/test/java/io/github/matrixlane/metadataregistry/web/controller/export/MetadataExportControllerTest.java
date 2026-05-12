package io.github.matrixlane.metadataregistry.web.controller.export;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.controller.MetadataExportController;
import io.github.matrixlane.metadataregistry.web.dto.export.AdministeredItemExport;
import io.github.matrixlane.metadataregistry.web.dto.export.ExportTaskResponse;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportRequest;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportResult;
import io.github.matrixlane.metadataregistry.web.entity.ExportTask;
import io.github.matrixlane.metadataregistry.web.entity.ExportTaskStatus;
import io.github.matrixlane.metadataregistry.web.repository.ExportTaskRepository;
import io.github.matrixlane.metadataregistry.web.service.MetadataExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link MetadataExportController} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class MetadataExportControllerTest {

    @Mock
    private MetadataExportService exportService;

    @Mock
    private ExportTaskRepository exportTaskRepository;

    private MetadataExportController controller;

    private MetadataExportRequest request;

    @BeforeEach
    void setUp() {
        controller = new MetadataExportController(exportService, exportTaskRepository);

        request = new MetadataExportRequest(
                null, null, null,
                null, null, null, null,
                null, null
        );
    }

    // =========================================================================
    // 1. 同步导出
    // =========================================================================

    @Test
    @DisplayName("POST 同步导出：正常返回结果")
    void exportSync_shouldReturnResult() {
        var metadata = new MetadataExportResult.ExportMetadata(
                "1.0.0", LocalDateTime.now(), 2, java.util.Map.of());
        var items = List.of(
                new AdministeredItemExport(UUID.randomUUID(), "DataElement", "Test", null,
                        null, null, null, null, null,
                        LocalDateTime.now(), LocalDateTime.now(), null, null, null));
        var result = new MetadataExportResult(metadata, items);

        when(exportService.shouldUseAsyncExport(any())).thenReturn(false);
        when(exportService.export(any())).thenReturn(result);

        @SuppressWarnings("unchecked")
        ApiResponse<?> response = controller.exportSync(request);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isInstanceOf(MetadataExportResult.class);
        MetadataExportResult data = (MetadataExportResult) response.getData();
        assertThat(data.items()).hasSize(1);
        assertThat(data.items().get(0).itemType()).isEqualTo("DataElement");
    }

    @Test
    @DisplayName("POST 同步导出：超限时返回建议使用异步")
    void exportSync_overflow_shouldReturnSuggestion() {
        when(exportService.shouldUseAsyncExport(any())).thenReturn(true);
        when(exportService.estimateCount(any())).thenReturn(15000L);

        @SuppressWarnings("unchecked")
        ApiResponse<?> response = controller.exportSync(request);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isInstanceOf(MetadataExportController.SyncExportOverflowResponse.class);

        var overflow = (MetadataExportController.SyncExportOverflowResponse) response.getData();
        assertThat(overflow.estimatedCount()).isEqualTo(15000L);
        assertThat(overflow.message()).contains("同步导出上限");
    }

    // =========================================================================
    // 2. 流式导出
    // =========================================================================

    @Test
    @DisplayName("GET 流式导出：应返回 JSON 附件")
    void exportStream_shouldReturnJsonAttachment() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write("{\"exportMetadata\":{\"schemaVersion\":\"1.0.0\"},\"items\":[]}".getBytes());
        doAnswer(inv -> {
            java.io.OutputStream os = inv.getArgument(1);
            os.write(baos.toByteArray());
            return null;
        }).when(exportService).exportJsonToStream(any(), any());

        var response = controller.exportStream(request);

        assertThat(response).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("json");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("metadata-export.json");
        assertThat(response.getBody()).isNotEmpty();
    }

    // =========================================================================
    // 3. CSV 导出
    // =========================================================================

    @Test
    @DisplayName("GET CSV 导出：应返回 CSV 附件")
    void exportCsv_shouldReturnCsvAttachment() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write("ID,ItemType,ItemLabel\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        doAnswer(inv -> {
            java.io.OutputStream os = inv.getArgument(1);
            os.write(baos.toByteArray());
            return null;
        }).when(exportService).exportCsvToStream(any(), any());

        var response = controller.exportCsv(request);

        assertThat(response).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("csv");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("metadata-export.csv");
        assertThat(response.getBody()).isNotEmpty();
    }

    // =========================================================================
    // 4. 异步导出
    // =========================================================================

    @Test
    @DisplayName("POST 异步导出：创建任务并执行")
    void createAsyncExport_shouldCreateAndExecuteTask() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.nameUUIDFromBytes("testuser".getBytes());

        ExportTask pendingTask = ExportTask.builder()
                .id(taskId)
                .status(ExportTaskStatus.PENDING)
                .processedCount(0)
                .totalCount(50)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(exportService.createAsyncExportTask(any(), any())).thenReturn(pendingTask);

        var response = controller.createAsyncExport(request, () -> "testuser");

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isInstanceOf(ExportTaskResponse.class);
        ExportTaskResponse taskResponse = (ExportTaskResponse) response.getData();
        assertThat(taskResponse.id()).isEqualTo(taskId);
        assertThat(taskResponse.status()).isEqualTo(ExportTaskStatus.PENDING);
    }

    @Test
    @DisplayName("GET 导出任务状态：查询已完成任务")
    void getAsyncExportStatus_completedTask_shouldReturnStatus() {
        UUID taskId = UUID.randomUUID();
        ExportTask completedTask = ExportTask.builder()
                .id(taskId)
                .status(ExportTaskStatus.COMPLETED)
                .processedCount(100)
                .totalCount(100)
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(exportTaskRepository.findById(taskId)).thenReturn(Optional.of(completedTask));
        when(exportService.getExportFileSize(taskId)).thenReturn(2048L);

        var response = controller.getAsyncExportStatus(taskId);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(0);
        ExportTaskResponse taskResponse = (ExportTaskResponse) response.getData();
        assertThat(taskResponse.status()).isEqualTo(ExportTaskStatus.COMPLETED);
        assertThat(taskResponse.processedCount()).isEqualTo(100);
        assertThat(taskResponse.fileSize()).isEqualTo(2048L);
    }

    @Test
    @DisplayName("GET 导出任务状态：查询失败任务")
    void getAsyncExportStatus_failedTask_shouldReturnErrorInfo() {
        UUID taskId = UUID.randomUUID();
        ExportTask failedTask = ExportTask.builder()
                .id(taskId)
                .status(ExportTaskStatus.FAILED)
                .processedCount(50)
                .totalCount(100)
                .errorMessage("Database connection timeout")
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(exportTaskRepository.findById(taskId)).thenReturn(Optional.of(failedTask));

        var response = controller.getAsyncExportStatus(taskId);

        assertThat(response).isNotNull();
        ExportTaskResponse taskResponse = (ExportTaskResponse) response.getData();
        assertThat(taskResponse.status()).isEqualTo(ExportTaskStatus.FAILED);
        assertThat(taskResponse.errorMessage()).isEqualTo("Database connection timeout");
    }

    // =========================================================================
    // 5. 异步下载与分片
    // =========================================================================

    @Test
    @DisplayName("GET 下载异步结果：已完成任务返回文件内容")
    void downloadAsyncExport_completedTask_shouldReturnFile() {
        UUID taskId = UUID.randomUUID();
        ExportTask completedTask = ExportTask.builder()
                .id(taskId)
                .status(ExportTaskStatus.COMPLETED)
                .resultFilePath("C:\\temp\\mdr-export-test.json")
                .totalCount(3)
                .processedCount(3)
                .build();

        when(exportTaskRepository.findById(taskId)).thenReturn(Optional.of(completedTask));
        when(exportService.getExportFileSize(taskId)).thenReturn(500L);
        // The controller clamps chunk length to fileSize-offset
        when(exportService.readExportChunk(eq(taskId), eq(0L), eq(500))).thenReturn("test-data".getBytes());

        var response = controller.downloadAsyncExport(taskId, 0L, 4194304);

        assertThat(response).isNotNull();
        assertThat(response.getHeaders().getContentDisposition().getFilename()).contains(taskId.toString());
        assertThat(response.getHeaders().get("X-Export-File-Size")).contains("500");
        assertThat(response.getHeaders().get("X-Export-Task-Status")).contains("COMPLETED");
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    @DisplayName("GET 下载异步结果：任务未完成应报错")
    void downloadAsyncExport_incompleteTask_shouldThrow() {
        UUID taskId = UUID.randomUUID();
        ExportTask processingTask = ExportTask.builder()
                .id(taskId)
                .status(ExportTaskStatus.PROCESSING)
                .totalCount(100)
                .processedCount(30)
                .build();

        when(exportTaskRepository.findById(taskId)).thenReturn(Optional.of(processingTask));

        assertThatThrownBy(() ->
            controller.downloadAsyncExport(taskId, 0L, 1024))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("导出任务未完成");
    }
}
