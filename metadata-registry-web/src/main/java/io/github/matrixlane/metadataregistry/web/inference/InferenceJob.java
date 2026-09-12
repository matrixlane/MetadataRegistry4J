package io.github.matrixlane.metadataregistry.web.inference;

import io.github.matrixlane.metadataregistry.web.assistant.AssistantJob;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceCandidate;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * 内存推断作业（complete-metadata 任务的兼容作业类型，D5 零数据库表）。
 *
 * <p>继承统一协作作业内核 {@link AssistantJob}：候选结果存于多态 result，
 * {@link #getCandidates()} 透传既有推断响应结构；provider 引用用于取消时终止进程。
 */
@Getter
public class InferenceJob extends AssistantJob {

    InferenceJob(UUID itemId, String createdBy) {
        super("complete-metadata", itemId, null, createdBy);
    }

    @SuppressWarnings("unchecked")
    public List<InferenceCandidate> getCandidates() {
        return (List<InferenceCandidate>) getResult();
    }
}
