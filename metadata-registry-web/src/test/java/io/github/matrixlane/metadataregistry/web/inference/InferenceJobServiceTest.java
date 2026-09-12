package io.github.matrixlane.metadataregistry.web.inference;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceJobRequest;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceProviderOption;
import io.github.matrixlane.metadataregistry.web.inference.provider.FakeAcpServer;
import io.github.matrixlane.metadataregistry.web.inference.provider.ProcessRunner;
import io.github.matrixlane.metadataregistry.web.inference.provider.RunningProcess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
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
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 推断作业服务测试：真实注册表（内置 claude/codex/kimi ACP 常驻进程）+
 * 脚本化假 ACP server runner。每个成功路径用例的脚本顺序固定为
 * [claude, codex, kimi]（注册表按内置顺序握手探测；作业由所解析
 * 提供方的常驻进程直接服务会话）。
 */
@ExtendWith(MockitoExtension.class)
class InferenceJobServiceTest {

    @Mock private InferencePromptBuilder promptBuilder;
    private final CandidateJsonExtractor extractor = new CandidateJsonExtractor();

    private final InferenceProperties properties = new InferenceProperties();

    @TempDir
    Path tempDir;

    private InferenceAgentSettingsService settingsService;
    private ScriptedRunner runner;
    private InferenceJobService service;

    @BeforeEach
    void setUp() {
        properties.getCli().setWorkDir(tempDir.resolve("mdr-inference").toString());
        settingsService = new InferenceAgentSettingsService(tempDir.resolve("inference-agents.yml"));
        runner = new ScriptedRunner();
        InferenceProviderRegistry registry = new InferenceProviderRegistry(properties, settingsService, runner);
        service = new InferenceJobService(properties, promptBuilder, extractor, registry, Runnable::run);
        lenient().when(promptBuilder.build(any(), anyList(), isNull(), isNull(), anyInt()))
                .thenReturn("prompt");
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

    /** 成功路径脚本：3 个内置 Agent 常驻进程（均预录作业应答，由实际选用的提供方消费）。 */
    private void enqueueSuccessScript(String rawOutput) {
        runner.enqueue(serverWithReply(rawOutput), serverWithReply(rawOutput), serverWithReply(rawOutput));
    }

    private FakeAcpServer serverWithReply(String rawOutput) {
        FakeAcpServer server = new FakeAcpServer();
        server.promptReplies.add(rawOutput);
        return server;
    }

    /** 探测失败的假 server：initialize 握手被拒。 */
    private FakeAcpServer probeFail() {
        FakeAcpServer server = new FakeAcpServer();
        server.failHandshake = true;
        return server;
    }

    @Test
    void createJob_capturesPromptAndRawOutput_forDebugTracing() {
        login("alice");
        enqueueSuccessScript("{\"candidates\":[{\"field\":\"definition\",\"text\":\"定义\"}]}");

        UUID jobId = service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, null));

        InferenceJob job = service.getJob(jobId);
        assertThat(job.getPrompt()).isNotBlank();
        assertThat(job.getRawOutput()).contains("定义");
    }

    @Test
    void createJob_success_returnsSucceededJobWithCandidates() {
        login("alice");
        enqueueSuccessScript("{\"candidates\":[{\"field\":\"definition\",\"language\":\"zh-CN\","
                + "\"text\":\"患者的唯一标识。\",\"rationale\":\"与指称一致\"}]}");

        UUID jobId = service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, null));

        InferenceJob job = service.getJob(jobId);
        assertThat(job.getStatus()).isEqualTo(InferenceJobStatus.SUCCEEDED);
        assertThat(job.getCandidates()).hasSize(1);
        assertThat(job.getCandidates().get(0).text()).isEqualTo("患者的唯一标识。");
        assertThat(job.getCreatedBy()).isEqualTo("alice");
        assertThat(job.getError()).isNull();
        // 未指定提供方：回退静态配置旧语义 cli → claude
        assertThat(job.getProviderId()).isEqualTo("claude");
        assertThat(job.getProviderDisplayName()).isEqualTo("Claude Code (ACP)");
    }

    @Test
    void createJob_explicitProvider_usesRequestedAgent() {
        login("alice");
        enqueueSuccessScript("{\"candidates\":[{\"field\":\"definition\",\"text\":\"定义\"}]}");

        UUID jobId = service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, "codex"));

        InferenceJob job = service.getJob(jobId);
        assertThat(job.getStatus()).isEqualTo(InferenceJobStatus.SUCCEEDED);
        assertThat(job.getProviderId()).isEqualTo("codex");
        assertThat(job.getProviderDisplayName()).isEqualTo("Codex CLI (ACP)");
    }

    @Test
    void createJob_providerUnavailable_failsImmediatelyWithReason() {
        login("alice");
        // claude 握手被拒（Agent 未安装/未登录）
        runner.enqueue(probeFail(), new FakeAcpServer(), new FakeAcpServer());

        UUID jobId = service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, null));

        InferenceJob job = service.getJob(jobId);
        assertThat(job.getStatus()).isEqualTo(InferenceJobStatus.FAILED);
        assertThat(job.getError()).contains("推断提供方不可用");
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void createJob_unknownProvider_throwsValidationFailed() {
        login("alice");
        runner.enqueue(new FakeAcpServer(), new FakeAcpServer(), new FakeAcpServer());

        assertThatThrownBy(() -> service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, "nope")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED));
    }

    @Test
    void createJob_disabledProviderViaSettings_throwsValidationFailed() {
        login("alice");
        settingsService.save(new InferenceAgentSettings(InferenceAgentSettings.FORMAT_VERSION, null,
                java.util.Map.of("claude", new InferenceAgentSettings.AgentSetting(
                        false, null, null, null))));
        runner.enqueue(new FakeAcpServer(), new FakeAcpServer(), new FakeAcpServer());

        assertThatThrownBy(() -> service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, "claude")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED));
    }

    @Test
    void createJob_defaultProviderFromSettings_isUsedWhenRequestOmitsProvider() {
        login("alice");
        settingsService.save(new InferenceAgentSettings(InferenceAgentSettings.FORMAT_VERSION, "codex",
                java.util.Map.of()));
        enqueueSuccessScript("{\"candidates\":[{\"field\":\"definition\",\"text\":\"定义\"}]}");

        UUID jobId = service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, null));

        InferenceJob job = service.getJob(jobId);
        assertThat(job.getStatus()).isEqualTo(InferenceJobStatus.SUCCEEDED);
        assertThat(job.getProviderId()).isEqualTo("codex");
    }

    @Test
    void createJob_invalidField_throwsValidationFailed() {
        login("alice");

        assertThatThrownBy(() -> service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("name"), null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("definition / designation");
    }

    @Test
    void createJob_candidateCountExceedsMax_isClampedInPromptAndResult() {
        login("alice");
        enqueueSuccessScript(fiveDefinitionCandidates());
        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        when(promptBuilder.build(any(), anyList(), isNull(), isNull(), countCaptor.capture()))
                .thenReturn("prompt");

        UUID jobId = service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, 10, null));

        assertThat(countCaptor.getValue()).isEqualTo(3);
        assertThat(service.getJob(jobId).getCandidates()).hasSize(3);
    }

    @Test
    void createJob_unparseableOutput_failsWithSnippet() {
        login("alice");
        enqueueSuccessScript("no json here");

        UUID jobId = service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, null));

        InferenceJob job = service.getJob(jobId);
        assertThat(job.getStatus()).isEqualTo(InferenceJobStatus.FAILED);
        assertThat(job.getError()).contains("no json here");
    }

    @Test
    void getJob_otherUser_getsNotFound() {
        login("alice");
        enqueueSuccessScript("{\"candidates\":[{\"field\":\"definition\",\"text\":\"定义\"}]}");
        UUID jobId = service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, null));

        login("bob");
        assertThatThrownBy(() -> service.getJob(jobId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getJob_missingJob_getsNotFound() {
        login("alice");
        assertThatThrownBy(() -> service.getJob(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getJob_superAdmin_canAccessOthersJob() {
        login("alice");
        enqueueSuccessScript("{\"candidates\":[{\"field\":\"definition\",\"text\":\"定义\"}]}");
        UUID jobId = service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, null));

        loginSuperAdmin();
        assertThat(service.getJob(jobId).getCreatedBy()).isEqualTo("alice");
    }

    @Test
    void cancelJob_runningJob_sendsSessionCancelAndCancels() throws Exception {
        login("alice");
        InferenceProviderRegistry registry = new InferenceProviderRegistry(properties, settingsService, runner);
        InferenceJobService asyncService = new InferenceJobService(properties, promptBuilder, extractor,
                registry, Executors.newSingleThreadExecutor());
        // 默认提供方 claude 的常驻进程挂起 prompt，等待取消
        FakeAcpServer claude = new FakeAcpServer();
        claude.hangOnPrompt = true;
        runner.enqueue(claude, new FakeAcpServer(), new FakeAcpServer());

        UUID jobId = asyncService.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, null));
        // 等 prompt 已发出（server 侧挂起）
        long promptDeadline = System.currentTimeMillis() + 5000;
        while (claude.pendingPromptId == null && System.currentTimeMillis() < promptDeadline) {
            Thread.sleep(10);
        }

        InferenceJob cancelled = asyncService.cancelJob(jobId);

        assertThat(cancelled.getStatus()).isEqualTo(InferenceJobStatus.CANCELLED);
        // ACP 取消路径：发送 session/cancel 通知（对端结束 turn），不直接杀常驻进程
        assertThat(claude.cancelNotificationReceived).isTrue();
        // 等异步任务结束后状态仍为 CANCELLED（取消优先于成功）
        long doneDeadline = System.currentTimeMillis() + 5000;
        while (asyncService.getJob(jobId).getStatus() == InferenceJobStatus.RUNNING
                && System.currentTimeMillis() < doneDeadline) {
            Thread.sleep(20);
        }
        assertThat(asyncService.getJob(jobId).getStatus()).isEqualTo(InferenceJobStatus.CANCELLED);
    }

    @Test
    void cancelJob_otherUser_getsNotFound() {
        login("alice");
        enqueueSuccessScript("{\"candidates\":[{\"field\":\"definition\",\"text\":\"定义\"}]}");
        UUID jobId = service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, null));

        login("bob");
        assertThatThrownBy(() -> service.cancelJob(jobId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void availableProviderOptions_onlyEnabledAndAvailable() {
        login("alice");
        // claude 可用；codex / kimi 探测失败
        runner.enqueue(new FakeAcpServer(), probeFail(), probeFail());

        List<InferenceProviderOption> options = service.availableProviderOptions();

        assertThat(options).hasSize(1);
        assertThat(options.get(0).id()).isEqualTo("claude");
        assertThat(options.get(0).displayName()).isEqualTo("Claude Code (ACP)");
        assertThat(options.get(0).isDefault()).isTrue();
    }

    @Test
    void disabledFeature_allOperationsRejected() {
        properties.setEnabled(false);
        login("alice");

        assertThatThrownBy(() -> service.availableProviderOptions())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ApiCodes.FEATURE_NOT_ENABLED));
        assertThatThrownBy(() -> service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getJob(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.cancelJob(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void purgeExpiredJobs_removesOldFinishedJobs() {
        login("alice");
        enqueueSuccessScript("{\"candidates\":[{\"field\":\"definition\",\"text\":\"定义\"}]}");
        UUID jobId = service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, null));
        InferenceJob job = service.getJob(jobId);
        ReflectionTestUtils.setField(job, "finishedAt", LocalDateTime.now().minusMinutes(31));

        service.purgeExpiredJobs();

        assertThatThrownBy(() -> service.getJob(jobId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void purgeExpiredJobs_keepsRecentJobs() {
        login("alice");
        enqueueSuccessScript("{\"candidates\":[{\"field\":\"definition\",\"text\":\"定义\"}]}");
        UUID jobId = service.createJob(UUID.randomUUID(),
                new InferenceJobRequest(List.of("definition"), null, null, null, null));

        service.purgeExpiredJobs();

        assertThat(service.getJob(jobId).getStatus()).isEqualTo(InferenceJobStatus.SUCCEEDED);
    }

    private String fiveDefinitionCandidates() {
        StringBuilder sb = new StringBuilder("{\"candidates\":[");
        for (int i = 1; i <= 5; i++) {
            if (i > 1) {
                sb.append(',');
            }
            sb.append("{\"field\":\"definition\",\"text\":\"定义").append(i).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
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