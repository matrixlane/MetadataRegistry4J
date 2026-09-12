package io.github.matrixlane.metadataregistry.web.inference;

/**
 * 推断作业状态机。
 */
public enum InferenceJobStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
