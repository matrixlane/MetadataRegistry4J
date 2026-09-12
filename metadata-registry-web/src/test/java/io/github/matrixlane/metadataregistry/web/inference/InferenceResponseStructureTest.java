package io.github.matrixlane.metadataregistry.web.inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceJobRequest;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceJobResponse;
import io.github.matrixlane.metadataregistry.web.inference.provider.FakeAcpServer;
import io.github.matrixlane.metadataregistry.web.inference.provider.ProcessRunner;
import io.github.matrixlane.metadataregistry.web.inference.provider.RunningProcess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 既有推断端点响应结构不变式：泛化后 /api/v1/inference 作业响应的 JSON 字段集合
 * 与泛化前完全一致（锁定端点契约，前端零改动）。
 */
@ExtendWith(MockitoExtension.class)
class InferenceResponseStructureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Mock
    private InferencePromptBuilder promptBuilder;

    @TempDir
    Path tempDir;

    private InferenceJobService service;

    @BeforeEach
    void setUp() {
        InferenceProperties properties = new InferenceProperties();
        properties.getCli().setWorkDir(tempDir.resolve("mdr-inference").toString());
        InferenceAgentSettingsService settingsService =
                new InferenceAgentSettingsService(tempDir.resolve("agents.yml"));
        ScriptedRunner runner = new ScriptedRunner();
        // 3 个内置 Agent 常驻进程均预录作业应答；默认提供方 claude 的进程服务作业
        for (int i = 0; i < 3; i++) {
            FakeAcpServer server = new FakeAcpServer();
            server.promptReplies.add(
                    "{\"candidates\":[{\"field\":\"definition\",\"text\":\"generated text\"}]}");
            runner.enqueue(server);
        }
        InferenceProviderRegistry registry = new InferenceProviderRegistry(properties, settingsService, runner);
        service = new InferenceJobService(properties, promptBuilder, new CandidateJsonExtractor(),
                registry, Runnable::run);
        lenient().when(promptBuilder.build(any(), anyList(), isNull(), isNull(), anyInt()))
                .thenReturn("prompt");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jobResponse_jsonFieldSet_unchangedAfterGeneralization() throws Exception {
        UUID jobId = service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, null));

        JsonNode json = MAPPER.valueToTree(InferenceJobResponse.of(service.getJob(jobId)));

        Set<String> fields = json.properties().stream().map(java.util.Map.Entry::getKey)
                .collect(Collectors.toSet());
        assertThat(fields).containsExactlyInAnyOrder(
                "id", "itemId", "status", "candidates", "error",
                "providerId", "providerDisplayName", "createdAt", "finishedAt");
        assertThat(json.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(json.get("providerId").asText()).isEqualTo("claude");
        assertThat(json.get("candidates")).hasSize(1);
        assertThat(json.get("candidates").get(0).get("text").asText()).isEqualTo("generated text");
    }

    /** 脚本化进程 runner。 */
    static class ScriptedRunner implements ProcessRunner {
        private final Deque<FakeAcpServer> script = new ArrayDeque<>();

        void enqueue(FakeAcpServer... servers) {
            for (FakeAcpServer server : servers) {
                script.addLast(server);
            }
        }

        @Override
        public RunningProcess start(List<String> command, Path workingDir) throws IOException {
            FakeAcpServer server = script.pollFirst();
            if (server == null) {
                throw new IOException("脚本化进程已耗尽：" + command);
            }
            return server;
        }
    }
}
