package io.github.matrixlane.metadataregistry.web.dto.mapping;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingSchemeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for creating or updating a mapping scheme.
 *
 * <p>Only basic fields are included; version, status, and parentId are
 * managed server-side.
 */
public record MappingSchemeRequest(

        @NotBlank(message = "方案名称不能为空")
        @Size(max = 255, message = "方案名称最多 255 个字符")
        String name,

        @NotBlank(message = "源标准 URI 不能为空")
        @Size(max = 2048, message = "源标准 URI 最多 2048 个字符")
        String sourceUri,

        @NotBlank(message = "目标标准 URI 不能为空")
        @Size(max = 2048, message = "目标标准 URI 最多 2048 个字符")
        String targetUri,

        @Size(max = 4000, message = "描述最多 4000 个字符")
        String description
) {
}
