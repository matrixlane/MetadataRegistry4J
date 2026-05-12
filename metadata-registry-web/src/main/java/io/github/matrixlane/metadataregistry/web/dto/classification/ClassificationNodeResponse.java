package io.github.matrixlane.metadataregistry.web.dto.classification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 分类节点的响应体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationNodeResponse {

    private UUID id;
    private String nodeCode;
    private String nodeName;
    private String description;
    private String definition;
    private Integer sortOrder;
    private UUID parentId;
    private UUID schemeId;
    private String schemeName;
    private boolean hasChildren;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
