package io.github.matrixlane.metadataregistry.web.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiAssistSettingsServiceTest {

    @TempDir
    Path tempDir;

    private Path settingsFile() {
        return tempDir.resolve("ai-assist.yml");
    }

    @Test
    void missingFile_returnsDefaultSettings() {
        AiAssistSettingsService service = new AiAssistSettingsService(settingsFile());

        assertThat(service.candidateCount()).isEqualTo(3);
        assertThat(service.current().hotkeysOrDefault())
                .containsEntry("aiDebug.toggle", "Ctrl+Shift+D");
    }

    @Test
    void save_persistsToFile_andReloadsIntoNewInstance() {
        AiAssistSettingsService service = new AiAssistSettingsService(settingsFile());
        service.save(new AiAssistSettings(AiAssistSettings.FORMAT_VERSION, 2,
                Map.of("aiDebug.toggle", "Ctrl+Alt+J", "my.cmd", "F5")));

        AiAssistSettingsService reloaded = new AiAssistSettingsService(settingsFile());

        assertThat(reloaded.candidateCount()).isEqualTo(2);
        assertThat(reloaded.current().hotkeysOrDefault())
                .containsEntry("aiDebug.toggle", "Ctrl+Alt+J")
                .containsEntry("my.cmd", "F5");
    }

    @Test
    void corruptedFile_fallsBackToDefaults() throws Exception {
        Files.writeString(settingsFile(), "generation: [1, 2");
        AiAssistSettingsService service = new AiAssistSettingsService(settingsFile());

        assertThat(service.candidateCount()).isEqualTo(3);
        assertThat(service.current().hotkeysOrDefault())
                .containsEntry("aiDebug.toggle", "Ctrl+Shift+D");
    }

    @Test
    void hotkeysOrDefault_mergesOverDefaults_andKeepsStoredOnlyKeys() {
        AiAssistSettings settings = new AiAssistSettings(AiAssistSettings.FORMAT_VERSION, null,
                Map.of("other.cmd", "Shift+1"));

        assertThat(settings.hotkeysOrDefault())
                .containsEntry("aiDebug.toggle", "Ctrl+Shift+D")
                .containsEntry("other.cmd", "Shift+1");
    }

    @Test
    void candidateCountOrDefault_clampsOutOfRange() {
        assertThat(new AiAssistSettings("1", 0, null).candidateCountOrDefault()).isEqualTo(1);
        assertThat(new AiAssistSettings("1", 9, null).candidateCountOrDefault()).isEqualTo(5);
        assertThat(new AiAssistSettings("1", null, null).candidateCountOrDefault()).isEqualTo(3);
    }
}
