package io.github.matrixlane.metadataregistry.web.bootstrap;

/**
 * 引导失败信息。stage 为失败步骤，code 为机器可读原因码，message 为面向用户的
 * 可读描述。message MUST NOT 包含数据库口令或完整连接串（见
 * {@link DatabaseBootstrapService#sanitizeForReporting}）。
 */
public record BootstrapFailure(BootstrapStage stage, String code, String message) {
}
