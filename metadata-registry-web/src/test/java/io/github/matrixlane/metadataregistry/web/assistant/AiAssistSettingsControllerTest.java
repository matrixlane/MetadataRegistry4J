package io.github.matrixlane.metadataregistry.web.assistant;

import io.github.matrixlane.metadataregistry.web.assistant.dto.AiAssistSettingsRequest;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistSettingsControllerTest {

    @Mock
    private AiAssistSettingsService settingsService;

    @Test
    void get_returnsMergedView() {
        when(settingsService.current()).thenReturn(
                new AiAssistSettings("1", 4, Map.of("my.cmd", "F9")));
        AiAssistSettingsController controller = new AiAssistSettingsController(settingsService);

        var response = controller.getSettings();

        assertThat(response.getData().candidateCount()).isEqualTo(4);
        assertThat(response.getData().hotkeys())
                .containsEntry("aiDebug.toggle", "Ctrl+Shift+D")
                .containsEntry("my.cmd", "F9");
    }

    @Test
    void put_valid_savesAndReturnsView() {
        when(settingsService.current()).thenReturn(
                new AiAssistSettings("1", 2, Map.of("aiDebug.toggle", "Ctrl+Alt+J")));
        AiAssistSettingsController controller = new AiAssistSettingsController(settingsService);

        var response = controller.saveSettings(
                new AiAssistSettingsRequest(2, Map.of("aiDebug.toggle", "Ctrl+Alt+J")));

        verify(settingsService).save(org.mockito.ArgumentMatchers.any(AiAssistSettings.class));
        assertThat(response.getData().candidateCount()).isEqualTo(2);
        assertThat(response.getData().hotkeys()).containsEntry("aiDebug.toggle", "Ctrl+Alt+J");
    }

    @Test
    void put_candidateCountOutOfRange_throwsValidationFailed() {
        AiAssistSettingsController controller = new AiAssistSettingsController(settingsService);

        assertThatThrownBy(() -> controller.saveSettings(new AiAssistSettingsRequest(6, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED));
        assertThatThrownBy(() -> controller.saveSettings(new AiAssistSettingsRequest(0, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void put_invalidHotkeyBinding_throwsValidationFailed() {
        AiAssistSettingsController controller = new AiAssistSettingsController(settingsService);

        assertThatThrownBy(() -> controller.saveSettings(
                new AiAssistSettingsRequest(3, Map.of("aiDebug.toggle", "Ctrl+Shift+Alt+D"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("顺序");
        assertThatThrownBy(() -> controller.saveSettings(
                new AiAssistSettingsRequest(3, Map.of("aiDebug.toggle", "Ctrl+Enter"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("主键");
        assertThatThrownBy(() -> controller.saveSettings(
                new AiAssistSettingsRequest(3, Map.of("aiDebug.toggle", "F13"))))
                .isInstanceOf(BusinessException.class);
    }
}
