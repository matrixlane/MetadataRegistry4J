package io.github.matrixlane.metadataregistry.web.inference.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 推断对话框可用的提供方选项（仅已启用且探测可用）。
 *
 * @param id          Agent id
 * @param displayName 展示名
 * @param isDefault   是否为默认提供方
 */
@Schema(description = "推断提供方选项（已启用且可用）")
public record InferenceProviderOption(
        String id,
        String displayName,
        boolean isDefault) {
}
