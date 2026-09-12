package io.github.matrixlane.metadataregistry.web.inference.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Agent 设置整体视图（GET/PUT/probe 统一响应）。
 */
@Schema(description = "推断 Agent 设置视图")
public record InferenceAgentsView(
        String defaultProvider,
        List<InferenceAgentView> agents) {
}
