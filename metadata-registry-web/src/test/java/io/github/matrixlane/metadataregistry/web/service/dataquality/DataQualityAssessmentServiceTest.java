package io.github.matrixlane.metadataregistry.web.service.dataquality;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.config.DataQualityProperties;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult;
import io.github.matrixlane.metadataregistry.web.entity.DataQualityTask;
import io.github.matrixlane.metadataregistry.web.entity.DataQualityTaskStatus;
import io.github.matrixlane.metadataregistry.web.repository.DataQualityTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataQualityAssessmentServiceTest {

    @Mock
    private DataQualityTaskRepository taskRepository;

    @Mock
    private JdbcDataQualityExecutor executor;

    @Captor
    private ArgumentCaptor<DataQualityTask> taskCaptor;

    private DataQualityAssessmentService service;
    private DataQualityProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DataQualityProperties();
        service = new DataQualityAssessmentService(taskRepository, properties, new ObjectMapper());
    }

    @Test
    void shouldHandleEmptyTable() {
        DataQualityAssessmentRequest request = DataQualityAssessmentRequest.forTable("empty_table", "ds1");
        when(executor.getExactRowCount("dbo", "empty_table")).thenReturn(0L);

        DataQualityAssessmentResult result = service.assess(executor, request);

        assertThat(result.isEmptyTable()).isTrue();
        assertThat(result.getSummary().getTotalColumns()).isZero();
        assertThat(result.getTableInfo().getEstimatedRowCount()).isZero();
    }

    @Test
    void shouldPerformFullAssessment() {
        DataQualityAssessmentRequest request = DataQualityAssessmentRequest.forTable("test_table", "ds1");
        when(executor.getExactRowCount("dbo", "test_table")).thenReturn(5L);

        Map<String, List<Object>> columnData = new HashMap<>();
        columnData.put("id", Arrays.asList(1, 2, 3, 4, 5));
        columnData.put("name", Arrays.asList("Alice", "Bob", "Charlie", "Diana", "Eve"));
        columnData.put("age", Arrays.asList(25, 30, 35, 40, 45));
        columnData.put("salary", Arrays.asList(50000, 60000, null, 80000, 90000));

        when(executor.fetchColumnData("dbo", "test_table", null, false, 5))
                .thenReturn(columnData);

        DataQualityAssessmentResult result = service.assess(executor, request);

        assertThat(result.isEmptyTable()).isFalse();
        assertThat(result.getNullValueStats()).hasSize(4);
        assertThat(result.getDistributionStats()).hasSize(4);
        assertThat(result.getOutlierStats()).isNotEmpty(); // age & salary should be numeric
        assertThat(result.getSummary().getTotalColumns()).isEqualTo(4);
        assertThat(result.getTableInfo().getTableName()).isEqualTo("test_table");

        // Check sampling info says full scan
        assertThat(result.getSamplingInfo().getMethod()).isEqualTo("full");
    }

    @Test
    void shouldCreateAsyncTask() {
        DataQualityAssessmentRequest request = DataQualityAssessmentRequest.forTable("big_table", "ds1");
        UUID userId = UUID.randomUUID();

        DataQualityTask savedTask = DataQualityTask.builder()
                .id(UUID.randomUUID())
                .status(DataQualityTaskStatus.PENDING)
                .progressPct(0)
                .stepName("pending")
                .tableName("big_table")
                .schemaName("dbo")
                .dataSourceKey("ds1")
                .createdAt(LocalDateTime.now())
                .createdBy(userId)
                .build();

        when(taskRepository.save(any(DataQualityTask.class))).thenReturn(savedTask);

        DataQualityTask task = service.createAsyncAssessment(executor, request, userId);

        assertThat(task.getStatus()).isEqualTo(DataQualityTaskStatus.PENDING);
        assertThat(task.getTableName()).isEqualTo("big_table");
        verify(taskRepository).save(any(DataQualityTask.class));
    }

    @Test
    void shouldExecuteAsyncTaskSuccessfully() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        DataQualityAssessmentRequest request = DataQualityAssessmentRequest.forTable("big_table", "ds1");

        String requestJson = "{\"tableName\":\"big_table\",\"schema\":\"dbo\",\"dataSourceKey\":\"ds1\"}";

        DataQualityTask pendingTask = DataQualityTask.builder()
                .id(taskId)
                .status(DataQualityTaskStatus.PENDING)
                .progressPct(0)
                .stepName("pending")
                .tableName("big_table")
                .schemaName("dbo")
                .dataSourceKey("ds1")
                .requestParams(requestJson)
                .createdAt(LocalDateTime.now())
                .createdBy(userId)
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(pendingTask));
        when(taskRepository.save(any(DataQualityTask.class))).thenAnswer(i -> i.getArgument(0));
        when(executor.getExactRowCount("dbo", "big_table")).thenReturn(5L);

        Map<String, List<Object>> columnData = new HashMap<>();
        columnData.put("id", Arrays.asList(1, 2, 3, 4, 5));
        when(executor.fetchColumnData("dbo", "big_table", null, false, 5))
                .thenReturn(columnData);

        service.executeAsyncAssessment(taskId, executor);

        verify(taskRepository, org.mockito.Mockito.times(2)).save(taskCaptor.capture());
        DataQualityTask captured = taskCaptor.getValue();

        assertThat(captured.getStatus()).isEqualTo(DataQualityTaskStatus.COMPLETED);
        assertThat(captured.getProgressPct()).isEqualTo(100);
        assertThat(captured.getResultJson()).isNotNull();
    }

    @Test
    void shouldHandleAsyncTaskFailure() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String requestJson = "{\"tableName\":\"big_table\",\"schema\":\"dbo\",\"dataSourceKey\":\"ds1\"}";

        DataQualityTask pendingTask = DataQualityTask.builder()
                .id(taskId)
                .status(DataQualityTaskStatus.PENDING)
                .progressPct(0)
                .stepName("pending")
                .tableName("big_table")
                .schemaName("dbo")
                .dataSourceKey("ds1")
                .requestParams(requestJson)
                .createdAt(LocalDateTime.now())
                .createdBy(userId)
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(pendingTask));
        when(executor.getExactRowCount("dbo", "big_table")).thenThrow(new RuntimeException("Connection failed"));

        service.executeAsyncAssessment(taskId, executor);

        verify(taskRepository, org.mockito.Mockito.times(2)).save(taskCaptor.capture());
        DataQualityTask captured = taskCaptor.getValue();

        assertThat(captured.getStatus()).isEqualTo(DataQualityTaskStatus.FAILED);
        assertThat(captured.getErrorMessage()).contains("Connection failed");
    }

    @Test
    void shouldGetTask() {
        UUID taskId = UUID.randomUUID();
        DataQualityTask task = DataQualityTask.builder()
                .id(taskId)
                .status(DataQualityTaskStatus.COMPLETED)
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        DataQualityTask found = service.getTask(taskId);
        assertThat(found.getId()).isEqualTo(taskId);
        assertThat(found.getStatus()).isEqualTo(DataQualityTaskStatus.COMPLETED);
    }

    @Test
    void shouldGetUserTasks() {
        UUID userId = UUID.randomUUID();
        service.getUserTasks(userId);
        verify(taskRepository).findByCreatedByOrderByCreatedAtDesc(userId);
    }
}
