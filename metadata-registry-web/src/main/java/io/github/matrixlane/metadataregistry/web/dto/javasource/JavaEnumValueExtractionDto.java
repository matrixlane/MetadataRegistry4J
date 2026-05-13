package io.github.matrixlane.metadataregistry.web.dto.javasource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO representing extracted information from an enum constant.
 */
@Schema(description = "提取的枚举值信息")
public record JavaEnumValueExtractionDto(

        @Schema(description = "枚举常量名", example = "ACTIVE")
        String name,

        @Schema(description = "枚举常量注释")
        String comment,

        @Schema(description = "是否已弃用")
        boolean deprecated,

        @Schema(description = "弃用原因")
        String deprecatedReason
) {
}
