package io.github.matrixlane.metadataregistry.web.inference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InferenceAgentSettingsServiceTest {

    @TempDir
    Path tempDir;

    private Path settingsFile() {
        return tempDir.resolve("inference-agents.yml");
    }

    @Test
    void missingFile_returnsEmptySettings() {
        InferenceAgentSettingsService service = new InferenceAgentSettingsService(settingsFile());

        assertThat(service.current().agents()).isEmpty();
        assertThat(service.current().defaultProvider()).isNull();
    }

    @Test
    void save_isImmediatelyVisibleInCurrent() {
        InferenceAgentSettingsService service = new InferenceAgentSettingsService(settingsFile());
        InferenceAgentSettings saved = new InferenceAgentSettings(InferenceAgentSettings.FORMAT_VERSION, "codex",
                Map.of("claude", new InferenceAgentSettings.AgentSetting(false, null, null, null)));

        service.save(saved);

        assertThat(service.current()).isSameAs(saved);
    }

    @Test
    void save_persistsToFile_andReloadsIntoNewInstance() {
        InferenceAgentSettingsService service = new InferenceAgentSettingsService(settingsFile());
        InferenceAgentSettings saved = new InferenceAgentSettings(InferenceAgentSettings.FORMAT_VERSION, "kimi",
                Map.of(
                        "kimi", new InferenceAgentSettings.AgentSetting(
                                true, "Kimi 本地版", "kimi-local", List.of("--fast")),
                        "mycli", new InferenceAgentSettings.AgentSetting(
                                null, null, "mycli", List.of("--json"))));
        service.save(saved);
        assertThat(settingsFile()).exists();

        InferenceAgentSettingsService reloaded = new InferenceAgentSettingsService(settingsFile());
        InferenceAgentSettings current = reloaded.current();

        assertThat(current.defaultProvider()).isEqualTo("kimi");
        assertThat(current.agent("kimi").displayName()).isEqualTo("Kimi 本地版");
        assertThat(current.agent("kimi").command()).isEqualTo("kimi-local");
        assertThat(current.agent("kimi").args()).containsExactly("--fast");
        // enabled 缺省（null）roundtrip 后仍为 null（回退默认启用语义）
        assertThat(current.agent("mycli").enabled()).isNull();
        assertThat(current.agent("mycli").command()).isEqualTo("mycli");
    }

    @Test
    void legacyV1File_builtinCommandArgsDropped_enabledAndDefaultKept() throws Exception {
        // v1 设置：内置条目带 CLI 时代 command/args 覆盖，自定义条目原样
        Files.writeString(settingsFile(),
                "version: 1\n"
                        + "defaultProvider: kimi\n"
                        + "agents:\n"
                        + "  claude:\n"
                        + "    enabled: false\n"
                        + "    displayName: 公司 Claude\n"
                        + "    command: claude\n"
                        + "    args: [-p, --output-format, text]\n"
                        + "  kimi:\n"
                        + "    command: kimi\n"
                        + "    args: [-p, '{prompt}']\n"
                        + "  myacp:\n"
                        + "    command: myacp-server\n"
                        + "    args: [--quiet]\n");
        InferenceAgentSettingsService service = new InferenceAgentSettingsService(settingsFile());
        InferenceAgentSettings current = service.current();

        // 内置条目：command/args 覆盖丢弃，enabled/displayName 保留
        assertThat(current.agent("claude").enabled()).isFalse();
        assertThat(current.agent("claude").displayName()).isEqualTo("公司 Claude");
        assertThat(current.agent("claude").command()).isNull();
        assertThat(current.agent("claude").args()).isNull();
        assertThat(current.agent("kimi").command()).isNull();
        assertThat(current.agent("kimi").args()).isNull();
        // 自定义条目原样保留（command/args 解释为 ACP server 启动命令）
        assertThat(current.agent("myacp").command()).isEqualTo("myacp-server");
        assertThat(current.agent("myacp").args()).containsExactly("--quiet");
        assertThat(current.defaultProvider()).isEqualTo("kimi");
        // 加载后内存态即为 v2，下次保存写 v2
        assertThat(current.version()).isEqualTo("2");
    }

    @Test
    void save_afterV1Load_writesVersion2() throws Exception {
        Files.writeString(settingsFile(),
                "version: 1\nagents:\n  kimi:\n    command: kimi\n    args: [-p]\n");
        InferenceAgentSettingsService service = new InferenceAgentSettingsService(settingsFile());

        service.save(service.current());

        assertThat(Files.readString(settingsFile())).contains("version: '2'");
    }

    @Test
    void legacyDirectKey_isIgnored() throws Exception {
        // 旧版设置文件中的 direct 段不再解析，未知键天然忽略
        Files.writeString(settingsFile(),
                "version: 1\ndefaultProvider: claude\ndirect:\n  enabled: false\n");
        InferenceAgentSettingsService service = new InferenceAgentSettingsService(settingsFile());

        assertThat(service.current().defaultProvider()).isEqualTo("claude");
        assertThat(service.current().agents()).isEmpty();
    }

    @Test
    void corruptedFile_fallsBackToEmpty_andSaveStillWorks() throws Exception {
        Files.writeString(settingsFile(), "agents: [1, 2");
        InferenceAgentSettingsService service = new InferenceAgentSettingsService(settingsFile());
        assertThat(service.current().agents()).isEmpty();

        // 损坏文件不阻断后续保存
        InferenceAgentSettings saved = new InferenceAgentSettings(InferenceAgentSettings.FORMAT_VERSION, null,
                Map.of("claude", new InferenceAgentSettings.AgentSetting(true, null, null, null)));
        service.save(saved);

        InferenceAgentSettingsService reloaded = new InferenceAgentSettingsService(settingsFile());
        assertThat(reloaded.current().agent("claude").enabled()).isTrue();
    }
}
