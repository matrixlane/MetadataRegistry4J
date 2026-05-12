package io.github.matrixlane.metadataregistry.web.dto.classification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 分类方案的响应体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationSchemeResponse {

    private UUID id;
    private String schemeName;
    private String description;
    private String version;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
