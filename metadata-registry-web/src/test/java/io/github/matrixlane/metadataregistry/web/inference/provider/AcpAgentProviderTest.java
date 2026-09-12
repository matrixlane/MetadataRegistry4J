package io.github.matrixlane.metadataregistry.web.inference.provider;

import io.github.matrixlane.metadataregistry.web.inference.InferenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcpAgentProviderTest {

    private static final List<String> COMMAND = List.of("fake-agent-acp");

    @TempDir
    Path tempDir;

    private String workDir;
    private final Deque<FakeAcpServer> script = new ArrayDeque<>();
    private final AtomicInteger startCalls = new AtomicInteger();
    private FakeAcpServer currentServer;

    private ProcessRunner runner = (command, workingDir) -> {
        startCalls.incrementAndGet();
        FakeAcpServer server = script.pollFirst();
        if (server == null) {
            throw new java.io.IOException("脚本化进程已耗尽，命令：" + command);
        }
        currentServer = server;
        return server;
    };

    private AcpAgentProvider provider(int timeoutSeconds) {
        return new AcpAgentProvider("fake", "Fake Agent", COMMAND, timeoutSeconds, workDir, runner);
    }

    @BeforeEach
    void setUp() {
        workDir = tempDir.resolve("mdr-inference").toString();
    }

    private FakeAcpServer enqueue() {
        FakeAcpServer server = new FakeAcpServer();
        script.addLast(server);
        return server;
    }

    @Test
    void status_handshakeOnce_isAvailableWithAgentInfo() {
        FakeAcpServer server = enqueue();
        server.agentName = "Codex";
        server.agentVersion = "1.10.0";
        AcpAgentProvider provider = provider(180);

        ProviderStatus first = provider.status();
        ProviderStatus second = provider.status();

        assertThat(first.available()).isTrue();
        assertThat(first.detail()).isEqualTo("Codex 1.10.0");
        assertThat(second.detail()).isEqualTo("Codex 1.10.0");
        assertThat(startCalls.get()).isEqualTo(1);
        // initialize 声明无 fs 能力
        assertThat(server.lastInitializeParams.path("clientCapabilities").path("fs").path("readTextFile").asBoolean())
                .isFalse();
        // protocolVersion 必须是 JSON 数字（ACP schema: ProtocolVersion = number；
        // 字符串 "1" 会被真实适配器以 Invalid params 拒绝——回归锁定）
        assertThat(server.lastInitializeParams.path("protocolVersion").isInt()).isTrue();
        assertThat(server.lastInitializeParams.path("protocolVersion").asInt()).isEqualTo(1);
    }

    @Test
    void probe_destroysProcessAndRehandshakes() {
        enqueue();
        FakeAcpServer second = enqueue();
        AcpAgentProvider provider = provider(180);
        provider.status();

        ProviderStatus probed = provider.probe();

        assertThat(startCalls.get()).isEqualTo(2);
        assertThat(probed.available()).isTrue();
        assertThat(currentServer).isSameAs(second);
    }

    @Test
    void status_handshakeError_isUnavailable() {
        FakeAcpServer server = enqueue();
        server.failHandshake = true;

        ProviderStatus status = provider(180).status();

        assertThat(status.available()).isFalse();
        assertThat(status.detail()).contains("不可用");
    }

    @Test
    void infer_success_accumulatesTextChunksAndRunsSessionLifecycle() throws Exception {
        FakeAcpServer server = enqueue();
        server.promptReplies.add("{\"candidates\":[");
        server.promptReplies.add("{\"field\":\"definition\",\"text\":\"定义\"}]}");
        AcpAgentProvider provider = provider(180);

        InferenceResult result = provider.infer(new InferenceRequest(UUID.randomUUID(), "请推断定义"));

        assertThat(result.rawOutput()).isEqualTo("{\"candidates\":[{\"field\":\"definition\",\"text\":\"定义\"}]}");
        // session/new：cwd 为 workDir 下独立临时目录，mcpServers 为空
        assertThat(server.receivedRequests.stream().anyMatch(r -> r.path("method").asText().equals("session/new")))
                .isTrue();
        var sessionNew = server.receivedRequests.stream()
                .filter(r -> r.path("method").asText().equals("session/new")).findFirst().orElseThrow();
        Path cwd = Path.of(sessionNew.path("params").path("cwd").asText());
        assertThat(cwd.getParent()).isEqualTo(Path.of(workDir).toAbsolutePath().normalize());
        assertThat(cwd.getFileName().toString()).startsWith("acp-");
        assertThat(Files.exists(cwd)).isFalse();
        // prompt：单文本块
        var prompt = server.receivedRequests.stream()
                .filter(r -> r.path("method").asText().equals("session/prompt")).findFirst().orElseThrow();
        assertThat(prompt.path("params").path("prompt")).hasSize(1);
        assertThat(prompt.path("params").path("prompt").get(0).path("text").asText()).isEqualTo("请推断定义");
        // thought 块被忽略：输出中不含 thinking
        assertThat(result.rawOutput()).doesNotContain("thinking");
        // 尽力 session/close
        assertThat(server.closeNotificationReceived).isTrue();
    }

    @Test
    void infer_processCrashesAfterHandshake_rebuildsAndSucceeds() {
        FakeAcpServer dying = enqueue();
        dying.dieAfterHandshake = true;
        FakeAcpServer healthy = enqueue();
        healthy.promptReplies.add("ok");
        AcpAgentProvider provider = provider(180);
        provider.status();

        InferenceResult result = provider.infer(new InferenceRequest(UUID.randomUUID(), "prompt"));

        assertThat(result.rawOutput()).isEqualTo("ok");
        assertThat(startCalls.get()).isEqualTo(2);
        assertThat(currentServer).isSameAs(healthy);
    }

    @Test
    void infer_promptTimeout_sendsCancelAndFails() {
        FakeAcpServer server = enqueue();
        server.hangOnPrompt = true;
        AcpAgentProvider provider = provider(1);

        assertThatThrownBy(() -> provider.infer(new InferenceRequest(UUID.randomUUID(), "prompt")))
                .isInstanceOf(InferenceException.class)
                .hasMessageContaining("超时");
        assertThat(server.cancelNotificationReceived).isTrue();
    }

    @Test
    void cancel_runningJob_serverEndsPromptAndInferReturns() throws Exception {
        FakeAcpServer server = enqueue();
        server.hangOnPrompt = true;
        AcpAgentProvider provider = provider(180);
        UUID jobId = UUID.randomUUID();
        String[] output = new String[1];
        Thread thread = new Thread(() -> output[0] =
                provider.infer(new InferenceRequest(jobId, "prompt")).rawOutput());
        thread.start();
        // 等 prompt 发出后取消
        long deadline = System.currentTimeMillis() + 5000;
        while (server.pendingPromptId == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        boolean cancelled = provider.cancel(jobId);

        assertThat(cancelled).isTrue();
        thread.join(5000);
        assertThat(output[0]).isNotNull();
        assertThat(server.cancelNotificationReceived).isTrue();
    }

    @Test
    void cancel_unknownJob_returnsFalse() {
        assertThat(provider(180).cancel(UUID.randomUUID())).isFalse();
    }

    @Test
    void infer_reverseRequestFromServer_isAnsweredMethodNotFound() {
        FakeAcpServer server = enqueue();
        server.sendReverseRequest = true;
        server.promptReplies.add("done");
        AcpAgentProvider provider = provider(180);

        InferenceResult result = provider.infer(new InferenceRequest(UUID.randomUUID(), "prompt"));

        assertThat(result.rawOutput()).isEqualTo("done");
        assertThat(server.reverseRequestErrorResponse).isNotNull();
        assertThat(server.reverseRequestErrorResponse.path("error").path("code").asLong()).isEqualTo(-32601);
    }

    @Test
    void destroy_killsProcess() {
        FakeAcpServer server = enqueue();
        AcpAgentProvider provider = provider(180);
        provider.status();

        provider.destroy();

        assertThat(server.destroyed).isTrue();
        // 销毁后再次使用自动重建
        enqueue();
        assertThat(provider.status().available()).isTrue();
        assertThat(startCalls.get()).isEqualTo(2);
    }
}
