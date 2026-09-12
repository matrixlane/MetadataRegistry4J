package io.github.matrixlane.metadataregistry.web.inference.provider;

import java.util.UUID;

/**
 * 提供方执行一次推断所需的最小输入。prompt 由 InferencePromptBuilder 组装，
 * 命令/参数等敏感内容只允许来自服务端配置，绝不进入请求体。
 *
 * @param jobId  作业标识，用于取消时定位进程
 * @param prompt 组装完成的中文提示词
 */
public record InferenceRequest(UUID jobId, String prompt) {
}
