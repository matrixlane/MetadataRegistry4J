package io.github.matrixlane.metadataregistry.web.inference;

import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceAgentView;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceProviderOption;
import io.github.matrixlane.metadataregistry.web.inference.provider.FakeAcpServer;
import io.github.matrixlane.metadataregistry.web.inference.provider.ProcessRunner;
import io.github.matrixlane.metadataregistry.web.inference.provider.RunningProcess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InferenceProviderRegistryTest {

    @TempDir
    Path tempDir;

    private InferenceProperties properties;
    private InferenceAgentSettingsService settingsService;
    private ScriptedRunner runner;
    private InferenceProviderRegistry registry;

    @BeforeEach
    void setUp() {
        properties = new InferenceProperties();
        properties.getCli().setWorkDir(tempDir.resolve("mdr-inference").toString());
        settingsService = new InferenceAgentSettingsService(tempDir.resolve("inference-agents.yml"));
        runner = new ScriptedRunner();
        registry = new InferenceProviderRegistry(properties, settingsService, runner);
    }

    /** 三个内置 ACP Agent 探测全部成功（每个常驻进程一次 initialize 握手）。 */
    private void enqueueAllProbesOk() {
        runner.enqueue(new FakeAcpServer(), new FakeAcpServer(), new FakeAcpServer());
    }

    private InferenceAgentView viewOf(List<InferenceAgentView> views, String id) {
        return views.stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void list_containsThreeBuiltins() {
        enqueueAllProbesOk();

        List<InferenceAgentView> views = registry.list();

        assertThat(views).hasSize(3);
        InferenceAgentView claude = viewOf(views, "claude");
        assertThat(claude.builtin()).isTrue();
        assertThat(claude.kind()).isEqualTo("acp");
        assertThat(claude.command()).isEqualTo("claude-agent-acp");
        assertThat(claude.args()).isEmpty();
        assertThat(claude.enabled()).isTrue();
        assertThat(claude.available()).isTrue();
        assertThat(claude.detail()).contains("FakeAgent");
        InferenceAgentView codex = viewOf(views, "codex");
        assertThat(codex.command()).isEqualTo("codex-acp");
        assertThat(codex.args()).isEmpty();
        InferenceAgentView kimi = viewOf(views, "kimi");
        assertThat(kimi.command()).isEqualTo("kimi");
        assertThat(kimi.args()).containsExactly("acp");
        // 旧语义 cli → claude 为默认
        assertThat(claude.isDefault()).isTrue();
    }

    @Test
    void list_appliesSettingsOverrides_forEnabledAndDisplayNameOnly() {
        // v2 语义：内置条目的 command/args 覆盖已废弃（v1 迁移时丢弃），
        // 设置仅保留 enabled/displayName 覆盖
        settingsService.save(new InferenceAgentSettings(InferenceAgentSettings.FORMAT_VERSION, null,
                Map.of("claude", new InferenceAgentSettings.AgentSetting(
                                true, "公司 Claude", "claude-pro", List.of("--team")),
                        "codex", new InferenceAgentSettings.AgentSetting(false, null, null, null))));
        enqueueAllProbesOk();

        List<InferenceAgentView> views = registry.list();

        InferenceAgentView claude = viewOf(views, "claude");
        assertThat(claude.displayName()).isEqualTo("公司 Claude");
        assertThat(claude.command()).isEqualTo("claude-agent-acp");
        assertThat(claude.args()).isEmpty();
        InferenceAgentView codex = viewOf(views, "codex");
        assertThat(codex.enabled()).isFalse();
        assertThat(codex.available()).isTrue();
    }

    @Test
    void resolveEnabled_disabledAgent_returnsEmpty() {
        settingsService.save(new InferenceAgentSettings(InferenceAgentSettings.FORMAT_VERSION, null,
                Map.of("claude", new InferenceAgentSettings.AgentSetting(false, null, null, null))));
        enqueueAllProbesOk();

        assertThat(registry.resolveEnabled("claude")).isEmpty();
        assertThat(registry.resolveEnabled("kimi")).isPresent();
        assertThat(registry.resolveEnabled("nope")).isEmpty();
        assertThat(registry.resolveEnabled(" ")).isEmpty();
    }

    @Test
    void defaultProviderId_settingsPreferred_overLegacy() {
        assertThat(registry.defaultProviderId()).isEqualTo("claude");

        properties.setProvider("kimi");
        assertThat(registry.defaultProviderId()).isEqualTo("kimi");

        settingsService.save(new InferenceAgentSettings(InferenceAgentSettings.FORMAT_VERSION, "codex",
                Map.of()));
        assertThat(registry.defaultProviderId()).isEqualTo("codex");
    }

    @Test
    void availableOptions_onlyEnabledAndAvailable() {
        // claude 可用，codex 握手被拒，kimi 进程在握手前即崩溃（EOF）
        FakeAcpServer claudeProbe = new FakeAcpServer();
        FakeAcpServer codexProbe = new FakeAcpServer();
        codexProbe.failHandshake = true;
        FakeAcpServer kimiProbe = new FakeAcpServer();
        kimiProbe.die();
        runner.enqueue(claudeProbe, codexProbe, kimiProbe);

        List<InferenceProviderOption> options = registry.availableOptions();

        assertThat(options).hasSize(1);
        assertThat(options.get(0).id()).isEqualTo("claude");
        assertThat(options.get(0).isDefault()).isTrue();
    }

    @Test
    void probeAll_forcesRebuildAndReprobe() {
        // list() 首次构建握手 3 个内置 Agent；probeAll 重建常驻进程并强制再握手一轮
        enqueueAllProbesOk();
        enqueueAllProbesOk();
        registry.list();
        int callsAfterList = runner.calls.get();
        assertThat(callsAfterList).isEqualTo(3);

        List<InferenceAgentView> views = registry.probeAll();

        assertThat(runner.calls.get() - callsAfterList).isEqualTo(3);
        assertThat(views).hasSize(3);
        assertThat(viewOf(views, "claude").available()).isTrue();
    }

    @Test
    void staticCommandOverride_appliesToDefaultProviderAgent() {
        // mdr.inference.cli.command 非空时覆盖默认提供方（默认 claude）所指的内置 Agent
        properties.getCli().setCommand("myacp");
        properties.getCli().setArgs(List.of("--stdio"));
        enqueueAllProbesOk();

        List<InferenceAgentView> views = registry.list();

        InferenceAgentView claude = viewOf(views, "claude");
        assertThat(claude.command()).isEqualTo("myacp");
        assertThat(claude.args()).containsExactly("--stdio");
        // 其余内置 Agent 不受影响
        assertThat(viewOf(views, "codex").command()).isEqualTo("codex-acp");
    }

    @Test
    void customAgent_withCommand_appearsAsNonBuiltin() {
        settingsService.save(new InferenceAgentSettings(InferenceAgentSettings.FORMAT_VERSION, null,
                Map.of("myacp", new InferenceAgentSettings.AgentSetting(
                        true, "我的 ACP Agent", "myacp-server", List.of("--quiet")))));
        enqueueAllProbesOk();
        runner.enqueue(new FakeAcpServer());

        List<InferenceAgentView> views = registry.list();

        assertThat(views).hasSize(4);
        InferenceAgentView custom = viewOf(views, "myacp");
        assertThat(custom.builtin()).isFalse();
        assertThat(custom.kind()).isEqualTo("acp");
        assertThat(custom.displayName()).isEqualTo("我的 ACP Agent");
        assertThat(custom.command()).isEqualTo("myacp-server");
        assertThat(custom.args()).containsExactly("--quiet");
        assertThat(custom.available()).isTrue();
    }

    @Test
    void customAgent_missingCommand_isSkipped() {
        settingsService.save(new InferenceAgentSettings(InferenceAgentSettings.FORMAT_VERSION, null,
                Map.of("broken", new InferenceAgentSettings.AgentSetting(true, null, " ", null))));
        enqueueAllProbesOk();

        List<InferenceAgentView> views = registry.list();

        assertThat(views).hasSize(3);
        assertThat(views.stream().anyMatch(v -> v.id().equals("broken"))).isFalse();
    }

    /** 脚本化进程 runner：按入队顺序逐次返回假 ACP server。 */
    static class ScriptedRunner implements ProcessRunner {

        private final Deque<FakeAcpServer> script = new ArrayDeque<>();
        final AtomicInteger calls = new AtomicInteger();

        void enqueue(FakeAcpServer... servers) {
            for (FakeAcpServer server : servers) {
                script.addLast(server);
            }
        }

        @Override
        public RunningProcess start(List<String> command, Path workingDir) throws IOException {
            calls.incrementAndGet();
            FakeAcpServer server = script.pollFirst();
            if (server == null) {
                throw new IOException("脚本化进程已耗尽，命令：" + command);
            }
            return server;
        }
    }
}
