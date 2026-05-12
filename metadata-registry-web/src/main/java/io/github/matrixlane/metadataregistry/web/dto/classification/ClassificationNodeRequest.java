package io.github.matrixlane.metadataregistry.web.dto.classification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 创建/更新分类节点的请求体。
 */
@Data
public class ClassificationNodeRequest {

    private UUID schemeId;

    @NotBlank(message = "节点编码不能为空")
    @Size(max = 255, message = "节点编码长度不能超过255字符")
    private String nodeCode;

    @NotBlank(message = "节点名称不能为空")
    @Size(max = 255, message = "节点名称长度不能超过255字符")
    private String nodeName;

    @Size(max = 4000, message = "描述长度不能超过4000字符")
    private String description;

    @Size(max = 4000, message = "定义长度不能超过4000字符")
    private String definition;

    private Integer sortOrder;

    private UUID parentId;
}
