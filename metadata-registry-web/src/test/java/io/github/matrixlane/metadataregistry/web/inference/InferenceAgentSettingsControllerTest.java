package io.github.matrixlane.metadataregistry.web.inference;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceAgentSettingsRequest;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceAgentView;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceAgentsView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceAgentSettingsControllerTest {

    @Mock
    private InferenceAgentSettingsService settingsService;
    @Mock
    private InferenceProviderRegistry registry;

    private InferenceAgentSettingsController controller;

    @BeforeEach
    void setUp() {
        controller = new InferenceAgentSettingsController(settingsService, registry);
    }

    private InferenceAgentView cliView(String id) {
        return new InferenceAgentView(id, id, "cli", id, List.of(), true, true,
                "CLI 可用", false, true);
    }

    @Test
    void getAgents_returnsMergedView() {
        when(registry.defaultProviderId()).thenReturn("claude");
        when(registry.list()).thenReturn(List.of(cliView("claude"), cliView("codex")));

        ApiResponse<InferenceAgentsView> response = controller.getAgents();

        assertThat(response.getData().defaultProvider()).isEqualTo("claude");
        assertThat(response.getData().agents()).hasSize(2);
    }

    @Test
    void saveAgents_validRequest_savesAndReturnsView() {
        when(registry.defaultProviderId()).thenReturn("claude");
        when(registry.list()).thenReturn(List.of(cliView("claude")));
        InferenceAgentSettingsRequest request = new InferenceAgentSettingsRequest("claude",
                List.of(new InferenceAgentSettingsRequest.AgentItem(
                        "claude", "公司 Claude", true, null, null)));

        ApiResponse<InferenceAgentsView> response = controller.saveAgents(request);

        ArgumentCaptor<InferenceAgentSettings> captor = ArgumentCaptor.forClass(InferenceAgentSettings.class);
        verify(settingsService).save(captor.capture());
        InferenceAgentSettings saved = captor.getValue();
        assertThat(saved.defaultProvider()).isEqualTo("claude");
        assertThat(saved.agent("claude").displayName()).isEqualTo("公司 Claude");
        assertThat(saved.agent("claude").enabled()).isTrue();
        assertThat(response.getData().agents()).hasSize(1);
    }

    @Test
    void saveAgents_customAgentWithoutCommand_throwsValidationFailed() {
        InferenceAgentSettingsRequest request = new InferenceAgentSettingsRequest(null,
                List.of(new InferenceAgentSettingsRequest.AgentItem("mycli", null, true, null, null)));

        assertThatThrownBy(() -> controller.saveAgents(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED));
        verify(settingsService, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveAgents_defaultProviderDisabled_throwsValidationFailed() {
        InferenceAgentSettingsRequest request = new InferenceAgentSettingsRequest("claude",
                List.of(new InferenceAgentSettingsRequest.AgentItem("claude", null, false, null, null)));

        assertThatThrownBy(() -> controller.saveAgents(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED));
        verify(settingsService, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveAgents_directItem_isTreatedAsCustomAndRejected() {
        // direct 已移除：id 为 direct 的条目不再特殊，缺 command 按自定义条目校验拒绝
        InferenceAgentSettingsRequest request = new InferenceAgentSettingsRequest(null,
                List.of(new InferenceAgentSettingsRequest.AgentItem("direct", null, false, null, null)));

        assertThatThrownBy(() -> controller.saveAgents(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED));
        verify(settingsService, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void probeAgents_returnsProbeView() {
        when(registry.defaultProviderId()).thenReturn("claude");
        when(registry.probeAll()).thenReturn(List.of(cliView("claude")));

        ApiResponse<InferenceAgentsView> response = controller.probeAgents();

        assertThat(response.getData().defaultProvider()).isEqualTo("claude");
        assertThat(response.getData().agents()).hasSize(1);
    }
}
