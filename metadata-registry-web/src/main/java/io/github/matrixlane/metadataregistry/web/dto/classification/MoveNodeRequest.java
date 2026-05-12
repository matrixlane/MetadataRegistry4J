package io.github.matrixlane.metadataregistry.web.dto.classification;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 移动分类节点的请求体。
 */
@Data
public class MoveNodeRequest {

    @NotNull(message = "目标父节点ID不能为空")
    private UUID targetParentId;
}
