package io.github.matrixlane.metadataregistry.web.inference.provider;

/**
 * 提供方原始执行结果。候选解析统一由 {@code CandidateJsonExtractor} 完成。
 *
 * @param rawOutput ACP 会话累积文本（agent_message_chunk 文本块拼接）
 */
public record InferenceResult(String rawOutput) {
}
