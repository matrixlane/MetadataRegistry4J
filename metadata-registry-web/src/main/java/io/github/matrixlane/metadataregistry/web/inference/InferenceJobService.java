package io.github.matrixlane.metadataregistry.web.inference;

import io.github.matrixlane.metadataregistry.web.assistant.AssistantJob;
import io.github.matrixlane.metadataregistry.web.assistant.AssistantJobService;
import io.github.matrixlane.metadataregistry.web.assistant.task.CompleteMetadataTask;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceJobRequest;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceProviderOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 推断作业服务（兼容外观）：保留既有 /api/v1/inference/** 端点的请求/响应结构
 * 与行为，内部委托统一协作作业内核（complete-metadata 任务）。
 *
 * <p>生产路径由 Spring 注入共享的 {@link AssistantJobService} 内核；
 * 五参构造器为既有调用方（含 18 例作业测试）保持泛化前的签名与独立内核。
 */
@Service
public class InferenceJobService {

    private final AssistantJobService kernel;

    @Autowired
    public InferenceJobService(AssistantJobService kernel) {
        this.kernel = kernel;
    }

    /** 兼容构造器（签名同泛化前）：自建独立内核，complete-metadata 作业创建为 InferenceJob。 */
    public InferenceJobService(InferenceProperties properties,
                               InferencePromptBuilder promptBuilder,
                               CandidateJsonExtractor candidateJsonExtractor,
                               InferenceProviderRegistry providerRegistry,
                               @Qualifier("inferenceExecutor") Executor inferenceExecutor) {
        this.kernel = new AssistantJobService(properties,
                List.of(new CompleteMetadataTask(promptBuilder, candidateJsonExtractor,
                        new io.github.matrixlane.metadataregistry.web.assistant.AiAssistSettingsService())),
                providerRegistry, inferenceExecutor,
                (taskId, itemId, selection, createdBy) -> new InferenceJob(itemId, createdBy));
    }

    /**
     * 创建推断作业并异步执行，返回作业 id（委托 complete-metadata 任务）。
     *
     * <p>提供方不可用时作业直接置为 FAILED 并携带原因，仍返回作业 id 便于统一轮询。
     */
    public UUID createJob(UUID itemId, InferenceJobRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("fields", List.copyOf(request.fields()));
        if (request.language() != null && !request.language().isBlank()) {
            params.put("language", request.language());
        }
        if (request.contextId() != null) {
            params.put("contextId", request.contextId().toString());
        }
        // 候选数不再由请求指定：一律取 AI 辅助设置（请求体字段保留以兼容旧前端，值被忽略）
        return kernel.createJob(CompleteMetadataTask.TASK_ID, itemId, null, params, request.provider());
    }

    /** 查询作业（仅创建者或 SUPER_ADMIN 可见，其余按不存在处理，不泄露作业内容）。 */
    public InferenceJob getJob(UUID jobId) {
        return asInferenceJob(kernel.getJob(jobId));
    }

    /** 取消作业：RUNNING 时终止对应进程并置 CANCELLED；已结束的作业幂等返回。 */
    public InferenceJob cancelJob(UUID jobId) {
        return asInferenceJob(kernel.cancelJob(jobId));
    }

    /** 推断对话框可选提供方：仅已启用且探测可用的 Agent。 */
    public List<InferenceProviderOption> availableProviderOptions() {
        return kernel.availableProviderOptions();
    }

    /** 当前登录用户名；无认证（系统路径）时为 system。 */
    public String currentUser() {
        return kernel.currentUser();
    }

    /** 每 5 分钟清理已结束超过 30 分钟的作业（内核 @Scheduled 统一执行）。 */
    public void purgeExpiredJobs() {
        kernel.purgeExpiredJobs();
    }

    /** 仅供测试与诊断：当前内存作业数。 */
    int jobCount() {
        return kernel.jobCount();
    }

    private static InferenceJob asInferenceJob(AssistantJob job) {
        if (job instanceof InferenceJob inferenceJob) {
            return inferenceJob;
        }
        throw new ResourceNotFoundException("InferenceJob", job.getId());
    }
}
