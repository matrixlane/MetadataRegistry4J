package io.github.matrixlane.metadataregistry.web.assistant;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.assistant.task.CompleteMetadataTask;
import io.github.matrixlane.metadataregistry.web.assistant.task.ExplainSelectionTask;
import io.github.matrixlane.metadataregistry.web.assistant.task.SelectionChatTask;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.inference.CandidateJsonExtractor;
import io.github.matrixlane.metadataregistry.web.inference.InferenceAgentSettingsService;
import io.github.matrixlane.metadataregistry.web.inference.InferenceJobStatus;
import io.github.matrixlane.metadataregistry.web.inference.InferencePromptBuilder;
import io.github.matrixlane.metadataregistry.web.inference.InferenceProperties;
import io.github.matrixlane.metadataregistry.web.inference.InferenceProviderRegistry;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceCandidate;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** 统一协作作业内核：任务 SPI 校验、多态结果、降级标注、越权隔离与 TTL 清理。 */
@ExtendWith(MockitoExtension.class)
class AssistantJobServiceTest {

    @Mock
    private InferencePromptBuilder promptBuilder;

    @TempDir
    Path tempDir;

    private InferenceAgentSettingsService settingsService;
    private ScriptedRunner runner;
    private AssistantJobService service;

    @BeforeEach
    void setUp() {
        InferenceProperties properties = new InferenceProperties();
        properties.getCli().setWorkDir(tempDir.resolve("mdr-inference").toString());
        settingsService = new InferenceAgentSettingsService(tempDir.resolve("agents.yml"));
        runner = new ScriptedRunner();
        InferenceProviderRegistry registry = new InferenceProviderRegistry(properties, settingsService, runner);
        service = new AssistantJobService(properties,
                List.of(new CompleteMetadataTask(promptBuilder, new CandidateJsonExtractor(),
                                new AiAssistSettingsService(tempDir.resolve("ai-assist.yml"))),
                        new ExplainSelectionTask(promptBuilder),
                        new SelectionChatTask(promptBuilder)),
                registry, Runnable::run, AssistantJob::new);
        lenient().when(promptBuilder.build(any(), anyList(), isNull(), isNull(), anyInt())).thenReturn("prompt");
        lenient().when(promptBuilder.buildItemContextSection(any())).thenReturn("【已知元数据】…");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void login(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private void loginSuperAdmin() {
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin", SystemRole.SUPER_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));
    }

    /** 脚本：3 个内置 Agent 常驻进程（均预录作业应答，由实际选用的提供方消费）。 */
    private void enqueueScript(String rawOutput) {
        for (int i = 0; i < 3; i++) {
            FakeAcpServer server = new FakeAcpServer();
            server.promptReplies.add(rawOutput);
            runner.enqueue(server);
        }
    }

    @Test
    void tasks_containsBuiltinTasks() {
        assertThat(service.tasks().stream().map(AssistantTask::id))
                .containsExactlyInAnyOrder("complete-metadata", "explain-selection", "selection-chat");
    }

    @Test
    void createJob_unknownTask_throwsValidationFailed() {
        login("alice");
        assertThatThrownBy(() -> service.createJob("nope", UUID.randomUUID(), null, Map.of(), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED));
    }

    @Test
    void createJob_itemIdRequiredTaskWithoutItem_throwsValidationFailed() {
        login("alice");
        runner.enqueue(new FakeAcpServer(), new FakeAcpServer(), new FakeAcpServer());
        assertThatThrownBy(() -> service.createJob("complete-metadata", null, null,
                Map.of("fields", List.of("definition")), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("itemId");
    }

    @Test
    void createJob_selectionRequiredTaskWithoutSelection_throwsValidationFailed() {
        login("alice");
        assertThatThrownBy(() -> service.createJob("explain-selection", null, null, Map.of(), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("selection");
    }

    @Test
    void createJob_candidatesTask_returnsTypedCandidates() {
        login("alice");
        UUID itemId = UUID.randomUUID();
        enqueueScript("{\"candidates\":[{\"field\":\"definition\",\"text\":\"定义文本\"}]}");

        UUID jobId = service.createJob("complete-metadata", itemId, null,
                Map.of("fields", List.of("definition")), null);

        AssistantJob job = service.getJob(jobId);
        assertThat(job.getStatus()).isEqualTo(InferenceJobStatus.SUCCEEDED);
        assertThat(job.getTaskId()).isEqualTo("complete-metadata");
        assertThat(job.getProviderId()).isEqualTo("claude");
        assertThat(job.getRawOutput()).contains("定义文本");
        @SuppressWarnings("unchecked")
        List<InferenceCandidate> candidates = (List<InferenceCandidate>) job.getResult();
        assertThat(candidates).hasSize(1);
        assertThat(job.isDegraded()).isFalse();
    }

    @Test
    void createJob_explainTask_textResultWithDegradedFlag() {
        login("alice");
        enqueueScript("这是通用解释。");

        UUID jobId = service.createJob("explain-selection", null,
                new Selection("definition", "permissible value", null, null, null, null), Map.of(), null);

        AssistantJob job = service.getJob(jobId);
        assertThat(job.getStatus()).isEqualTo(InferenceJobStatus.SUCCEEDED);
        assertThat(job.getResult()).isEqualTo("这是通用解释。");
        assertThat(job.isDegraded()).isTrue();
    }

    @Test
    void getJob_otherUser_getsNotFound() {
        login("alice");
        enqueueScript("{\"candidates\":[]}");
        UUID jobId = service.createJob("complete-metadata", UUID.randomUUID(),
                null, Map.of("fields", List.of("definition")), null);

        login("bob");
        assertThatThrownBy(() -> service.getJob(jobId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getJob_superAdmin_canAccessOthersJob() {
        login("alice");
        enqueueScript("{\"candidates\":[]}");
        UUID jobId = service.createJob("complete-metadata", UUID.randomUUID(),
                null, Map.of("fields", List.of("definition")), null);

        loginSuperAdmin();
        assertThat(service.getJob(jobId).getCreatedBy()).isEqualTo("alice");
    }

    @Test
    void purgeExpiredJobs_removesOldFinishedJobs() {
        login("alice");
        enqueueScript("{\"candidates\":[]}");
        UUID jobId = service.createJob("complete-metadata", UUID.randomUUID(),
                null, Map.of("fields", List.of("definition")), null);
        ReflectionTestUtils.setField(service.getJob(jobId), "finishedAt", LocalDateTime.now().minusMinutes(31));

        service.purgeExpiredJobs();

        assertThatThrownBy(() -> service.getJob(jobId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void disabledFeature_allOperationsRejected() {
        InferenceProperties properties = new InferenceProperties();
        properties.setEnabled(false);
        service = new AssistantJobService(properties,
                List.of(new ExplainSelectionTask(promptBuilder)),
                new InferenceProviderRegistry(properties, settingsService, runner),
                Runnable::run, AssistantJob::new);
        login("alice");

        assertThatThrownBy(() -> service.createJob("explain-selection", null,
                new Selection(null, "x", null, null, null, null), Map.of(), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.FEATURE_NOT_ENABLED));
        assertThatThrownBy(() -> service.getJob(UUID.randomUUID())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.cancelJob(UUID.randomUUID())).isInstanceOf(BusinessException.class);
    }

    @Test
    void run_capturesPromptAndRawOutput_onSuccess() {
        login("alice");
        enqueueScript("这是通用解释。");

        UUID jobId = service.createJob("explain-selection", null,
                new Selection("definition", "permissible value", null, null, null, null), Map.of(), null);

        AssistantJob job = service.getJob(jobId);
        assertThat(job.getStatus()).isEqualTo(InferenceJobStatus.SUCCEEDED);
        assertThat(job.getPrompt()).isNotBlank();
        assertThat(job.getRawOutput()).isEqualTo("这是通用解释。");
    }

    @Test
    void run_capturesPromptAndError_onFailure() {
        login("alice");
        for (int i = 0; i < 3; i++) {
            FakeAcpServer server = new FakeAcpServer();
            if (i == 0) {
                server.failPrompt = true;
                server.promptErrorMessage = "boom";
            }
            runner.enqueue(server);
        }

        UUID jobId = service.createJob("explain-selection", null,
                new Selection(null, "x", null, null, null, null), Map.of(), null);

        AssistantJob job = service.getJob(jobId);
        assertThat(job.getStatus()).isEqualTo(InferenceJobStatus.FAILED);
        assertThat(job.getPrompt()).isNotBlank();
        assertThat(job.getError()).isNotBlank();
    }

    @Test
    void recentExchanges_creatorIsolationAndSuperAdminSeesAll() {
        login("alice");
        enqueueScript("{\"candidates\":[]}");
        UUID aliceJob = service.createJob("complete-metadata", UUID.randomUUID(),
                null, Map.of("fields", List.of("definition")), null);
        login("bob");
        enqueueScript("{\"candidates\":[]}");
        UUID bobJob = service.createJob("complete-metadata", UUID.randomUUID(),
                null, Map.of("fields", List.of("definition")), null);

        login("alice");
        assertThat(service.recentExchanges()).extracting(AssistantJob::getId).containsExactly(aliceJob);
        loginSuperAdmin();
        assertThat(service.recentExchanges()).extracting(AssistantJob::getId)
                .containsExactlyInAnyOrder(aliceJob, bobJob);
    }

    @Test
    void recentExchanges_newestFirstAndCappedAt50() {
        login("alice");
        UUID last = null;
        for (int i = 0; i < 55; i++) {
            enqueueScript("{\"candidates\":[]}");
            last = service.createJob("complete-metadata", UUID.randomUUID(),
                    null, Map.of("fields", List.of("definition")), null);
        }

        List<AssistantJob> exchanges = service.recentExchanges();

        assertThat(exchanges).hasSize(AssistantJobService.DEBUG_EXCHANGE_LIMIT);
        assertThat(exchanges.get(0).getId()).isEqualTo(last);
        // 创建时间严格倒序
        assertThat(exchanges).extracting(AssistantJob::getCreatedAt)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
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
                throw new IOException("脚本化进程已耗尽：" + command);
            }
            return server;
        }
    }
}