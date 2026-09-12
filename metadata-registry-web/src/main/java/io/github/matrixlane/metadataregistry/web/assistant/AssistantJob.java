package io.github.matrixlane.metadataregistry.web.assistant;

import io.github.matrixlane.metadataregistry.web.inference.InferenceJobStatus;
import io.github.matrixlane.metadataregistry.web.inference.provider.MetadataInferenceProvider;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 统一协作作业（内存态，不持久化、不建表）。作业加 taskId/selection/多态 result，
 * 由 {@link AssistantJobService} 管理；complete-metadata 兼容外观使用其子类
 * {@code InferenceJob}（候选结果透传既有推断响应结构）。
 *
 * <p>result 按任务输出种类定型：CANDIDATES → List&lt;InferenceCandidate&gt;，
 * TEXT → String；rawOutput 保留 AI 原文供来源记录与重试使用。
 */
@Getter
public class AssistantJob {

    private final UUID id = UUID.randomUUID();
    private final String taskId;
    private final UUID itemId;
    private final Selection selection;
    private final String createdBy;
    private final LocalDateTime createdAt = LocalDateTime.now();

    private volatile InferenceJobStatus status = InferenceJobStatus.RUNNING;
    /** 多态结果（按任务输出种类定型）。 */
    private volatile Object result;
    /** 发给提供方的提示词原文（调试追踪，仅存内存）。 */
    @Setter
    private volatile String prompt;
    /** AI 原始输出（未结构化）。 */
    @Setter
    private volatile String rawOutput;
    /** 是否降级语境执行（如无关联项目的通用解释），响应中标注。 */
    @Setter
    private volatile boolean degraded;
    private volatile String error;
    private volatile LocalDateTime finishedAt;
    /** 创建作业时选定的提供方标识与展示名（响应透传）。 */
    @Setter
    private volatile String providerId;
    @Setter
    private volatile String providerDisplayName;
    /** 执行中的提供方，取消时据此终止进程。 */
    @Setter
    private volatile MetadataInferenceProvider provider;

    public AssistantJob(String taskId, UUID itemId, Selection selection, String createdBy) {
        this.taskId = taskId;
        this.itemId = itemId;
        this.selection = selection;
        this.createdBy = createdBy;
    }

    void succeed(Object result) {
        this.result = result;
        this.finishedAt = LocalDateTime.now();
        this.status = InferenceJobStatus.SUCCEEDED;
    }

    void fail(String error) {
        this.error = error;
        this.finishedAt = LocalDateTime.now();
        this.status = InferenceJobStatus.FAILED;
    }

    void cancel() {
        this.finishedAt = LocalDateTime.now();
        this.status = InferenceJobStatus.CANCELLED;
    }

    boolean isFinished() {
        return status != InferenceJobStatus.RUNNING;
    }
}
