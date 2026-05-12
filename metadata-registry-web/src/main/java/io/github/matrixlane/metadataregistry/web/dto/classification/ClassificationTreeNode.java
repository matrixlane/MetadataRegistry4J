package io.github.matrixlane.metadataregistry.web.dto.classification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 分类树的节点 DTO，用于前端 Element Plus Tree 渲染。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationTreeNode {

    private UUID id;
    private String label;
    private String nodeCode;
    private String description;
    private Integer sortOrder;
    private boolean hasChildren;
    private List<ClassificationTreeNode> children;
}
