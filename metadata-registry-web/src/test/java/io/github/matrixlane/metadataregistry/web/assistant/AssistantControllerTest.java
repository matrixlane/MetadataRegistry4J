package io.github.matrixlane.metadataregistry.web.assistant;

import io.github.matrixlane.metadataregistry.web.assistant.dto.AssistantJobRequest;
import io.github.matrixlane.metadataregistry.web.assistant.dto.AssistantJobResponse;
import io.github.matrixlane.metadataregistry.web.assistant.task.ExplainSelectionTask;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.inference.InferenceJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantControllerTest {

    @Mock
    private AssistantJobService jobService;

    private AssistantController controller;

    @BeforeEach
    void setUp() {
        controller = new AssistantController(jobService);
    }

    @Test
    void tasks_returnsTaskListWithSpecs() {
        when(jobService.tasks()).thenReturn(List.of(new ExplainSelectionTask(null) {
        }));

        ApiResponse<List<io.github.matrixlane.metadataregistry.web.assistant.dto.AssistantTaskView>> response =
                controller.tasks();

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).id()).isEqualTo("explain-selection");
        assertThat(response.getData().get(0).displayName()).isEqualTo("解释所选文本");
        assertThat(response.getData().get(0).outputKind().name()).isEqualTo("TEXT");
    }

    @Test
    void createJob_delegatesToKernel() {
        UUID jobId = UUID.randomUUID();
        when(jobService.createJob(anyString(), isNull(), any(), anyMap(), isNull())).thenReturn(jobId);
        AssistantJobRequest request = new AssistantJobRequest("explain-selection", null,
                new Selection("definition", "选段", null, null, null, null), Map.of("question", "q"), null);

        ApiResponse<Map<String, UUID>> response = controller.createJob(request);

        assertThat(response.getData().get("jobId")).isEqualTo(jobId);
        verify(jobService).createJob(eq("explain-selection"), isNull(), any(Selection.class),
                anyMap(), isNull());
    }

    @Test
    void getJob_mapsJobToResponse() {
        AssistantJob job = new AssistantJob("explain-selection", null,
                new Selection(null, "x", null, null, null, null), "alice");
        job.setProviderId("claude");
        job.setProviderDisplayName("Claude Code");
        job.setRawOutput("解释");
        job.succeed("解释");
        when(jobService.getJob(job.getId())).thenReturn(job);

        ApiResponse<AssistantJobResponse> response = controller.getJob(job.getId());

        AssistantJobResponse data = response.getData();
        assertThat(data.taskId()).isEqualTo("explain-selection");
        assertThat(data.status()).isEqualTo(InferenceJobStatus.SUCCEEDED);
        assertThat(data.result()).isEqualTo("解释");
        assertThat(data.providerId()).isEqualTo("claude");
        assertThat(data.providerDisplayName()).isEqualTo("Claude Code");
    }
}
