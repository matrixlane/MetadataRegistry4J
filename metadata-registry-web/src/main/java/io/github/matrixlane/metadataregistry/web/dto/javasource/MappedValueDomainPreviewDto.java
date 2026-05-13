package io.github.matrixlane.metadataregistry.web.dto.javasource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Preview of a ValueDomain mapped from a Java enum type.
 */
@Schema(description = "值域映射预览")
public record MappedValueDomainPreviewDto(

        @Schema(description = "名称", example = "GenderType")
        String name,

        @Schema(description = "描述")
        String description,

        @Schema(description = "数据类型", example = "STRING")
        String dataTypeName,

        @Schema(description = "来源枚举类全限定名")
        String sourceEnumClassName
) {
}
