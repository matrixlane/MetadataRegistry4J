package io.github.matrixlane.metadataregistry.web.dto.association;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 建立/解除关联的请求体。source / target 的角色由关联类型固定
 * （见 {@link StandardAssociationType} 各值的标准方向）。
 */
public record AssociationMutationRequest(
        @NotNull UUID sourceItemId,
        @NotNull UUID targetItemId) {
}
