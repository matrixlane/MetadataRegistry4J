package io.github.matrixlane.metadataregistry.web.dto.javasource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Preview of a DataElement mapped from a Java field comment.
 */
@Schema(description = "数据元映射预览")
public record MappedDataElementPreviewDto(

        @Schema(description = "名称", example = "patientName")
        String name,

        @Schema(description = "描述")
        String description,

        @Schema(description = "来源字段名")
        String sourceFieldName,

        @Schema(description = "来源类全限定名")
        String sourceClassName,

        @Schema(description = "是否可空")
        boolean nullable
) {
}
