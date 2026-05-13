package io.github.matrixlane.metadataregistry.web.dto.javasource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Preview of a DataElementConcept mapped from a Java class comment.
 */
@Schema(description = "数据元概念映射预览")
public record MappedDataElementConceptPreviewDto(

        @Schema(description = "名称", example = "Patient")
        String name,

        @Schema(description = "描述")
        String description,

        @Schema(description = "对象类名", example = "Patient")
        String objectClassName,

        @Schema(description = "属性名", example = "attributes")
        String propertyName,

        @Schema(description = "来源类全限定名")
        String sourceClassName
) {
}
