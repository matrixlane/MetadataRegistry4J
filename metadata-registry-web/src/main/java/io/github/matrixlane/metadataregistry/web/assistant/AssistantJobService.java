package io.github.matrixlane.metadataregistry.web.assistant;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.inference.InferenceJobStatus;
import io.github.matrixlane.metadataregistry.web.inference.InferenceProperties;
import io.github.matrixlane.metadataregistry.web.inference.InferenceProviderRegistry;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceProviderOption;
import io.github.matrixlane.metadataregistry.web.inference.provider.InferenceRequest;
import io.github.matrixlane.metadataregistry.web.inference.provider.MetadataInferenceProvider;
import io.github.matrixlane.metadataregistry.web.inference.provider.ProviderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一协作作业内核：以 {@link AssistantTask} SPI 执行任意协作场景。
 *
 * <p>复用推断接入层（{@link InferenceProviderRegistry} 解析提供方、inferenceExecutor
 * 异步执行、CLI 取消杀进程）、内存作业存储、创建者隔离与 TTL 清理；作业加 taskId、
 * selection 与多态 result（按任务输出种类定型）。泛化自原 InferenceJobService，
 * 既有 /api/v1/inference/** 经 InferenceJobService 兼容外观委托本内核。
 *
 * <p>本类不标注 @Service：由 {@code InferenceAsyncConfig} 以 @Bean 注册并注入
 * 作业工厂——complete-metadata 作业须创建为 InferenceJob 以保持推断响应结构兼容。
 */
@Slf4j
public class AssistantJobService {

    static final Duration JOB_TTL = Duration.ofMinutes(30);

    private final InferenceProperties properties;
    private final Map<String, AssistantTask> tasks;
    private final InferenceProviderRegistry providerRegistry;
    private final Executor inferenceExecutor;
    private final JobFactory jobFactory;

    private final Map<UUID, AssistantJob> jobs = new ConcurrentHashMap<>();

    public AssistantJobService(InferenceProperties properties,
                               List<AssistantTask> tasks,
                               InferenceProviderRegistry providerRegistry,
                               Executor inferenceExecutor,
                               JobFactory jobFactory) {
        this.properties = properties;
        this.tasks = tasks.stream().collect(Collectors.toMap(AssistantTask::id, Function.identity()));
        this.providerRegistry = providerRegistry;
        this.inferenceExecutor = inferenceExecutor;
        this.jobFactory = jobFactory;
    }

    /** 任务清单（前端据此动态渲染任务入口）。 */
    public List<AssistantTask> tasks() {
        return List.copyOf(tasks.values());
    }

    /**
     * 创建协作作业并异步执行，返回作业 id。
     *
     * <p>提供方不可用时作业直接置为 FAILED 并携带原因，仍返回作业 id 便于统一轮询。
     */
    public UUID createJob(String taskId, UUID itemId, Selection selection,
                          Map<String, Object> params, String provider) {
        assertEnabled();
        AssistantTask task = Optional.ofNullable(tasks.get(taskId))
                .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "未知的协作任务：" + taskId));
        TaskContext ctx = new TaskContext(itemId, selection, params, currentUser());
        if (task.inputSpec().itemIdRequired() && ctx.itemId() == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "任务 " + taskId + " 要求关联项目 itemId");
        }
        if (task.inputSpec().selection() == TaskInputSpec.SelectionRequirement.REQUIRED
                && ctx.selection() == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "任务 " + taskId + " 要求提供划选内容 selection");
        }
        task.validate(ctx);

        MetadataInferenceProvider resolved = resolveProvider(provider);
        AssistantJob job = jobFactory.create(task.id(), itemId, selection, ctx.caller());
        job.setDegraded(task.degraded(ctx));
        ProviderStatus status = resolved.status();
        job.setProviderId(resolved.id());
        job.setProviderDisplayName(status.displayName());
        if (!status.available()) {
            job.fail("推断提供方不可用：" + status.detail());
            jobs.put(job.getId(), job);
            return job.getId();
        }
        jobs.put(job.getId(), job);
        inferenceExecutor.execute(() -> run(job, task, ctx, resolved));
        return job.getId();
    }

    /** 查询作业（仅创建者或 SUPER_ADMIN 可见，其余按不存在处理，不泄露作业内容）。 */
    public AssistantJob getJob(UUID jobId) {
        assertEnabled();
        return visibleJob(jobId, currentUser());
    }

    /** 取消作业：RUNNING 时终止对应进程并置 CANCELLED；已结束的作业幂等返回。 */
    public AssistantJob cancelJob(UUID jobId) {
        assertEnabled();
        AssistantJob job = visibleJob(jobId, currentUser());
        if (job.getStatus() == InferenceJobStatus.RUNNING) {
            if (job.getProvider() != null) {
                job.getProvider().cancel(job.getId());
            }
            job.cancel();
        }
        return job;
    }

    /** 协作对话框可选提供方：仅已启用且探测可用的 Agent（与推断作业一致）。 */
    public List<InferenceProviderOption> availableProviderOptions() {
        assertEnabled();
        return providerRegistry.availableOptions();
    }

    /** 调试追踪列表上限。 */
    static final int DEBUG_EXCHANGE_LIMIT = 50;

    /**
     * 最近 AI 交互（调试追踪）：按创建时间倒序、上限 {@value #DEBUG_EXCHANGE_LIMIT} 条。
     * 普通用户仅返回本人创建的作业；SUPER_ADMIN 返回全部。仅存内存，随作业 TTL 清理。
     */
    public List<AssistantJob> recentExchanges() {
        assertEnabled();
        String username = currentUser();
        boolean all = isSuperAdmin();
        return jobs.values().stream()
                .filter(job -> all || job.getCreatedBy().equals(username))
                .sorted(java.util.Comparator.comparing(AssistantJob::getCreatedAt).reversed())
                .limit(DEBUG_EXCHANGE_LIMIT)
                .toList();
    }

    /** 当前登录用户名；无认证（系统路径）时为 system。 */
    public String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "system";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return user.username();
        }
        return auth.getName();
    }

    private void run(AssistantJob job, AssistantTask task, TaskContext ctx,
                     MetadataInferenceProvider provider) {
        job.setProvider(provider);
        try {
            String prompt = task.buildPrompt(ctx);
            job.setPrompt(prompt);
            String rawOutput = provider.infer(new InferenceRequest(job.getId(), prompt)).rawOutput();
            job.setRawOutput(rawOutput);
            Object parsed = task.parseOutput(ctx, rawOutput);
            // 取消与完成竞态时以取消为准
            if (job.getStatus() == InferenceJobStatus.RUNNING) {
                job.succeed(parsed);
            }
        } catch (io.github.matrixlane.metadataregistry.web.inference.InferenceException e) {
            failUnlessCancelled(job, e.getMessage());
        } catch (Exception e) {
            log.error("协作作业执行失败 jobId={} taskId={} itemId={}",
                    job.getId(), job.getTaskId(), job.getItemId(), e);
            failUnlessCancelled(job, "协作作业执行失败：" + e.getMessage());
        }
    }

    private void failUnlessCancelled(AssistantJob job, String error) {
        if (job.getStatus() == InferenceJobStatus.RUNNING) {
            job.fail(error);
        }
    }

    private AssistantJob visibleJob(UUID jobId, String username) {
        AssistantJob job = jobs.get(jobId);
        if (job == null || (!job.getCreatedBy().equals(username) && !isSuperAdmin())) {
            throw new ResourceNotFoundException("AssistantJob", jobId);
        }
        return job;
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getPrincipal() instanceof AuthenticatedUser user
                && user.role() == SystemRole.SUPER_ADMIN;
    }

    private MetadataInferenceProvider resolveProvider(String requested) {
        // 请求显式指定：仅已启用 Agent 可被选用，未知或已禁用 id 视为参数错误
        if (requested != null && !requested.isBlank()) {
            String id = requested.trim();
            return providerRegistry.resolveEnabled(id)
                    .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "未知的推断提供方或该 Agent 未启用：" + id));
        }
        // 未指定：使用设置中的默认提供方（无设置时回退静态配置旧语义，cli → claude）
        String defaultId = providerRegistry.defaultProviderId();
        return providerRegistry.resolveEnabled(defaultId)
                .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "默认推断提供方不可用或未启用：" + defaultId));
    }

    private void assertEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ApiCodes.FEATURE_NOT_ENABLED, "AI 元数据推断功能未启用");
        }
    }

    /** 每 5 分钟清理已结束超过 30 分钟的作业。 */
    @Scheduled(fixedRate = 300_000)
    public void purgeExpiredJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minus(JOB_TTL);
        jobs.values().removeIf(job -> job.isFinished() && job.getFinishedAt() != null
                && job.getFinishedAt().isBefore(cutoff));
    }

    /** 仅供测试与诊断：当前内存作业数。 */
    public int jobCount() {
        return jobs.size();
    }

    /** 作业实例工厂：complete-metadata 兼容外观据此创建 InferenceJob 子类实例。 */
    @FunctionalInterface
    public interface JobFactory {

        AssistantJob create(String taskId, UUID itemId, Selection selection, String createdBy);
    }
}
